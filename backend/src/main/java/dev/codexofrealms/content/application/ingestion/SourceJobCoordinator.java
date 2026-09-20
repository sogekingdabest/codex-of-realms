package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.application.port.*;
import dev.codexofrealms.content.application.source.SourceException;
import dev.codexofrealms.realm.RealmAccess;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
class SourceJobCoordinator {
    private final SourceJobRepository jobs;
    private final IngestionMetadataCoordinator metadata;
    private final RealmAccess access;

    SourceJobCoordinator(
            SourceJobRepository jobs, IngestionMetadataCoordinator metadata, RealmAccess access) {
        this.jobs = jobs;
        this.metadata = metadata;
        this.access = access;
    }

    record Prepared(SourceJob job, boolean writeFile, String copyFrom) {}

    @Transactional
    Prepared prepare(
            UUID realmId,
            UUID documentId,
            UUID userId,
            String title,
            UUID policyId,
            AcceptedSource source,
            String key,
            String hash,
            String config,
            String fingerprint) {
        access.requireEditor(realmId, userId);
        jobs.serializeSubmission(realmId, userId, key);
        Optional<SourceJob> existing = jobs.findSubmission(realmId, userId, key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash))
                throw new SourceJobException(
                        "source.idempotency_conflict",
                        "La clave ya se utilizó para una operación diferente.");
            return new Prepared(existing.get(), false, null);
        }
        String copyFrom = null;
        PreparedVersion prepared;
        if (documentId == null)
            prepared = metadata.create(realmId, userId, title, policyId, source, fingerprint);
        else if (source != null)
            prepared = metadata.replace(realmId, documentId, userId, policyId, source, fingerprint);
        else {
            copyFrom = metadata.lockLatestVersion(realmId, documentId, userId).storageKey();
            prepared = metadata.reprocess(realmId, documentId, userId, fingerprint);
        }
        SourceVersion version =
                prepared.isUnchanged()
                        ? metadata.activeVersion(realmId, documentId, userId)
                        : prepared.version();
        SourceJob job =
                jobs.insert(realmId, userId, version, key, hash, config, prepared.isUnchanged());
        return new Prepared(job, !prepared.isUnchanged(), copyFrom);
    }

    @Transactional
    void activate(
            SourceJob job,
            List<SourceChunk> chunks,
            List<float[]> embeddings,
            String provider,
            String model) {
        if (!jobs.lockCurrent(job)) return;
        metadata.activate(
                job.realmId(),
                job.requestedBy(),
                job.version(),
                chunks,
                embeddings,
                provider,
                model);
        jobs.succeeded(job);
    }

    @Transactional
    SourceJobView retry(UUID realmId, UUID id, UUID userId, String config) {
        access.requireEditor(realmId, userId);
        SourceJob observed = jobs.find(realmId, id).orElseThrow(SourceException::unavailable);
        SourceVersion latest =
                metadata.lockLatestVersion(realmId, observed.version().documentId(), userId);
        SourceJob job = jobs.lock(realmId, id).orElseThrow(SourceException::unavailable);
        if (!latest.versionId().equals(job.version().versionId()))
            throw new SourceJobException(
                    "source.job_not_retryable",
                    "Una versión posterior ha sustituido esta operación.");
        access.requireEditablePolicy(realmId, job.version().accessPolicyId(), userId);
        if (EnumSet.of(SourceJobState.UPLOADING, SourceJobState.QUEUED, SourceJobState.RUNNING)
                .contains(job.view().state())) return job.view();
        if (job.view().state() != SourceJobState.FAILED)
            throw new SourceJobException(
                    "source.job_not_retryable", "Esta operación ya no se puede reintentar.");
        if (!job.pipelineConfig().equals(config))
            throw new SourceJobException(
                    "source.pipeline_changed",
                    "La configuración ha cambiado. Reprocesa la fuente para crear una nueva"
                        + " versión.");
        jobs.retry(job, userId);
        return jobs.find(realmId, id).orElseThrow().view();
    }
}
