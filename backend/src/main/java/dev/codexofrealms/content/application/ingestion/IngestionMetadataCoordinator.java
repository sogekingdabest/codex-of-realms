package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.content.application.port.SourceVersion;
import dev.codexofrealms.content.application.source.SourceDocumentView;
import dev.codexofrealms.content.application.source.SourceException;
import dev.codexofrealms.content.domain.ProcessingStatus;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IngestionMetadataCoordinator {
    private final RealmAccess realmAccess;
    private final SourceRepository repository;

    IngestionMetadataCoordinator(RealmAccess realmAccess, SourceRepository repository) {
        this.realmAccess = realmAccess;
        this.repository = repository;
    }

    @Transactional
    PreparedVersion create(
        UUID realmId, UUID userId, String title, UUID policyId,
        AcceptedSource source, String fingerprint
    ) {
        authorize(realmId, policyId, userId);
        UUID documentId = UUID.randomUUID();
        String normalizedTitle = normalizeTitle(title);
        repository.createDocument(documentId, realmId, normalizedTitle, userId);
        return PreparedVersion.created(createVersion(
            new VersionContext(realmId, documentId, userId, normalizedTitle, policyId, fingerprint, 1),
            source
        ));
    }

    @Transactional
    PreparedVersion replace(
        UUID realmId, UUID documentId, UUID userId, UUID policyId,
        AcceptedSource source, String fingerprint
    ) {
        authorize(realmId, policyId, userId);
        SourceVersion active = repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceException::unavailable);
        if (active.checksum().equals(source.checksum())
            && active.accessPolicyId().equals(policyId)
            && active.pipelineFingerprint().equals(fingerprint)) {
            return PreparedVersion.unchanged(repository.findActiveView(realmId, documentId)
                .orElseThrow(SourceException::unavailable));
        }
        return PreparedVersion.created(createVersion(
            new VersionContext(
                realmId, documentId, userId, active.title(), policyId, fingerprint,
                repository.nextVersionNumber(realmId, documentId)
            ),
            source
        ));
    }

    @Transactional
    PreparedVersion reprocess(UUID realmId, UUID documentId, UUID userId, String fingerprint) {
        SourceVersion active = repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceException::unavailable);
        authorize(realmId, active.accessPolicyId(), userId);
        if (active.pipelineFingerprint().equals(fingerprint)) {
            return PreparedVersion.unchanged(repository.findActiveView(realmId, documentId)
                .orElseThrow(SourceException::unavailable));
        }
        return PreparedVersion.created(cloneVersion(
            new VersionContext(
                realmId, documentId, userId, active.title(), active.accessPolicyId(), fingerprint,
                repository.nextVersionNumber(realmId, documentId)
            ),
            active
        ));
    }

    @Transactional(readOnly = true)
    SourceVersion activeVersion(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        return repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceException::unavailable);
    }

    @Transactional
    void status(UUID versionId, ProcessingStatus status, String failureCode) {
        repository.setStatus(versionId, status, failureCode);
    }

    @Transactional
    SourceDocumentView activate(
        UUID realmId, UUID userId, SourceVersion version,
        List<SourceChunk> chunks, List<float[]> embeddings,
        String provider, String model
    ) {
        authorize(realmId, version.accessPolicyId(), userId);
        repository.activate(version, chunks, embeddings, provider, model);
        return repository.findActiveView(realmId, version.documentId())
            .orElseThrow(SourceException::unavailable);
    }

    private SourceVersion createVersion(VersionContext context, AcceptedSource source) {
        UUID versionId = UUID.randomUUID();
        String extension = source.mediaType().equals("text/plain") ? "txt" : "md";
        String key = context.realmId() + "/" + context.documentId() + "/" + versionId + "." + extension;
        SourceVersion version = new SourceVersion(
            context.documentId(), versionId, context.number(), context.title(),
            source.originalFilename(), source.mediaType(), "es", source.checksum(), key,
            context.policyId(), context.fingerprint()
        );
        return repository.createVersion(context.realmId(), context.userId(), version);
    }

    private SourceVersion cloneVersion(VersionContext context, SourceVersion active) {
        UUID versionId = UUID.randomUUID();
        String extension = active.mediaType().equals("text/plain") ? "txt" : "md";
        String key = context.realmId() + "/" + context.documentId() + "/" + versionId + "." + extension;
        SourceVersion version = new SourceVersion(
            context.documentId(), versionId, context.number(), context.title(),
            active.originalFilename(), active.mediaType(), active.language(), active.checksum(), key,
            context.policyId(), context.fingerprint()
        );
        return repository.createVersion(context.realmId(), context.userId(), version);
    }

    private void authorize(UUID realmId, UUID policyId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, policyId, userId);
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            throw IngestionException.invalidSource("The title must contain between 1 and 200 characters.");
        }
        return title.strip();
    }

    private record VersionContext(
        UUID realmId,
        UUID documentId,
        UUID userId,
        String title,
        UUID policyId,
        String fingerprint,
        int number
    ) {
    }
}
