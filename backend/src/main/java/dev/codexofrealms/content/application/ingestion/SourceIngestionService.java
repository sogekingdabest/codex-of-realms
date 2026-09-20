package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.application.port.*;
import dev.codexofrealms.content.application.source.SourceException;
import dev.codexofrealms.realm.RealmAccess;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class SourceIngestionService {
    private final SourceFileValidator validator;
    private final RawSourceStorage storage;
    private final SourceJobCoordinator coordinator;
    private final SourceJobRepository jobs;
    private final IngestionProperties properties;
    private final RealmAccess access;

    SourceIngestionService(
            SourceFileValidator validator,
            RawSourceStorage storage,
            SourceJobCoordinator coordinator,
            SourceJobRepository jobs,
            IngestionProperties properties,
            RealmAccess access) {
        this.validator = validator;
        this.storage = storage;
        this.coordinator = coordinator;
        this.jobs = jobs;
        this.properties = properties;
        this.access = access;
    }

    public SourceSubmission create(
            UUID realm,
            UUID user,
            String title,
            UUID policy,
            byte[] bytes,
            String filename,
            String type,
            String key) {
        access.requireEditor(realm, user);
        return submit(
                realm, null, user, title, policy, validator.validate(bytes, filename, type), key);
    }

    public SourceSubmission replace(
            UUID realm,
            UUID doc,
            UUID user,
            UUID policy,
            byte[] bytes,
            String filename,
            String type,
            String key) {
        access.requireEditor(realm, user);
        return submit(
                realm, doc, user, null, policy, validator.validate(bytes, filename, type), key);
    }

    public SourceSubmission reprocess(UUID realm, UUID doc, UUID user, String key) {
        return submit(realm, doc, user, null, null, null, key);
    }

    private SourceSubmission submit(
            UUID realm,
            UUID doc,
            UUID user,
            String title,
            UUID policy,
            AcceptedSource source,
            String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw IngestionException.invalidSource(
                    "Idempotency-Key debe contener entre 1 y 128 caracteres ASCII alfanuméricos,"
                        + " puntos, guiones o dos puntos.");
        String config = properties.toString();
        // Length-prefixed fields avoid ambiguity between arbitrary titles and filenames.
        String hash =
                hashFields(
                        doc,
                        title,
                        policy,
                        source == null ? null : source.checksum(),
                        source == null ? null : source.originalFilename(),
                        source == null ? null : source.mediaType(),
                        config);
        SourceJobCoordinator.Prepared prepared =
                coordinator.prepare(
                        realm,
                        doc,
                        user,
                        title,
                        policy,
                        source,
                        key,
                        hash,
                        config,
                        hashFields("structural-v1", config));
        SourceJob job = prepared.job();
        if (!prepared.writeFile() && job.view().state() == SourceJobState.UPLOADING) {
            try {
                if (!sha256(storage.read(job.version().storageKey()))
                        .equals(job.version().checksum())) throw new IllegalStateException();
                jobs.queue(job.view().id());
            } catch (RuntimeException exception) {
                throw new SourceJobException(
                        "source.upload_in_progress",
                        "La copia original sigue en curso. Repite la petición con la misma clave.");
            }
        }
        if (prepared.writeFile()) {
            try {
                byte[] bytes = source == null ? storage.read(prepared.copyFrom()) : source.bytes();
                if (!sha256(bytes).equals(job.version().checksum()))
                    throw new IllegalStateException("Checksum mismatch");
                storage.write(job.version().storageKey(), bytes);
                // A duplicate request may already have queued this intact original.
                // Keep it even if this transition loses the race or the job is cancelled.
                jobs.queue(job.view().id());
            } catch (RuntimeException exception) {
                jobs.uploadFailed(job.view().id(), "FILE_UNAVAILABLE");
                throw new SourceJobException(
                        "source.file_unavailable",
                        "No se pudo guardar el archivo. La operación quedó registrada para"
                            + " recuperarla.");
            }
        }
        byte[] original = source == null ? storage.read(job.version().storageKey()) : source.bytes();
        int excluded = dev.codexofrealms.content.application.evidence.VisiblePassageService
            .analyze(new String(original, StandardCharsets.UTF_8)).excludedSentences();
        return new SourceSubmission(jobs.find(realm, job.view().id()).orElseThrow().view(), excluded);
    }

    public List<SourceJobView> list(UUID realm, UUID user) {
        access.requireEditor(realm, user);
        return jobs.list(realm);
    }

    public SourceJobView get(UUID realm, UUID id, UUID user) {
        access.requireEditor(realm, user);
        return jobs.find(realm, id).orElseThrow(SourceException::unavailable).view();
    }

    public SourceJobView retry(UUID realm, UUID id, UUID user) {
        // Inspect access before reading a storage key or exposing any processing state.
        get(realm, id, user);
        SourceJob job = jobs.find(realm, id).orElseThrow(SourceException::unavailable);
        if (job.view().state() == SourceJobState.FAILED) {
            try {
                if (!sha256(storage.read(job.version().storageKey()))
                        .equals(job.version().checksum())) throw new IllegalStateException();
            } catch (RuntimeException exception) {
                throw new SourceJobException(
                        "source.file_unavailable",
                        "Falta el archivo original. Cárgalo nuevamente sobre este documento.");
            }
        }
        return coordinator.retry(realm, id, user, properties.toString());
    }

    static String hashFields(Object... fields) {
        StringBuilder value = new StringBuilder();
        for (Object field : fields) {
            String text = field == null ? "" : field.toString();
            value.append(text.length()).append(':').append(text);
        }
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
