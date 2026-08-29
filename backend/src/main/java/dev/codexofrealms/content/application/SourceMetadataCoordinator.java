package dev.codexofrealms.content.application;

import dev.codexofrealms.content.domain.ProcessingStatus;
import dev.codexofrealms.content.infrastructure.SourceJdbcRepository;
import dev.codexofrealms.content.infrastructure.SourceVersionRecord;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SourceMetadataCoordinator {

    private final RealmAccess realmAccess;
    private final SourceJdbcRepository repository;

    SourceMetadataCoordinator(RealmAccess realmAccess, SourceJdbcRepository repository) {
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
        repository.createDocument(documentId, realmId, normalizeTitle(title), userId);
        return PreparedVersion.created(createVersion(
            realmId, documentId, userId, normalizeTitle(title), policyId, source, fingerprint, 1
        ));
    }

    @Transactional
    PreparedVersion replace(
        UUID realmId, UUID documentId, UUID userId, UUID policyId,
        AcceptedSource source, String fingerprint
    ) {
        authorize(realmId, policyId, userId);
        SourceVersionRecord active = repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceNotFoundException::new);
        if (active.checksum().equals(source.checksum())
            && active.accessPolicyId().equals(policyId)
            && active.pipelineFingerprint().equals(fingerprint)) {
            return PreparedVersion.unchanged(repository.findActiveView(realmId, documentId).orElseThrow());
        }
        int number = repository.nextVersionNumber(realmId, documentId);
        return PreparedVersion.created(createVersion(
            realmId, documentId, userId, active.title(), policyId, source, fingerprint, number
        ));
    }

    @Transactional
    PreparedVersion reprocess(UUID realmId, UUID documentId, UUID userId, String fingerprint) {
        SourceVersionRecord active = repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceNotFoundException::new);
        authorize(realmId, active.accessPolicyId(), userId);
        if (active.pipelineFingerprint().equals(fingerprint)) {
            return PreparedVersion.unchanged(repository.findActiveView(realmId, documentId).orElseThrow());
        }
        return PreparedVersion.created(cloneVersion(
            realmId, documentId, userId, active.title(), active.accessPolicyId(),
            fingerprint, repository.nextVersionNumber(realmId, documentId), active
        ));
    }

    @Transactional(readOnly = true)
    SourceVersionRecord activeVersion(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        return repository.findActiveVersion(realmId, documentId)
            .orElseThrow(SourceNotFoundException::new);
    }

    @Transactional
    void status(UUID versionId, ProcessingStatus status, String failureCode) {
        repository.setStatus(versionId, status, failureCode);
    }

    @Transactional
    SourceDocumentView activate(
        UUID realmId, UUID userId, SourceVersionRecord version,
        List<SourceChunk> chunks, List<float[]> embeddings,
        String provider, String model
    ) {
        authorize(realmId, version.accessPolicyId(), userId);
        repository.activate(version, chunks, embeddings, provider, model);
        return repository.findActiveView(realmId, version.documentId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    List<SourceDocumentView> list(UUID realmId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.listAccessible(realmId, userId);
    }

    @Transactional(readOnly = true)
    SourceDocumentView get(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleView(realmId, documentId, userId)
            .orElseThrow(SourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    List<SourceChunkView> chunks(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        repository.findActiveView(realmId, documentId)
            .orElseThrow(SourceNotFoundException::new);
        return repository.listActiveChunks(realmId, documentId);
    }

    @Transactional(readOnly = true)
    SourceVersionRecord accessibleVersion(
        UUID realmId,
        UUID documentId,
        UUID versionId,
        UUID userId
    ) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleVersion(realmId, documentId, versionId, userId)
            .orElseThrow(SourceNotFoundException::new);
    }

    @Transactional
    List<String> retire(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        return repository.retireDocument(realmId, documentId);
    }

    private SourceVersionRecord createVersion(
        UUID realmId, UUID documentId, UUID userId, String title, UUID policyId,
        AcceptedSource source, String fingerprint, int number
    ) {
        UUID versionId = UUID.randomUUID();
        String extension = source.mediaType().equals("text/plain") ? "txt" : "md";
        String key = realmId + "/" + documentId + "/" + versionId + "." + extension;
        return repository.createVersion(documentId, versionId, realmId, number, title,
            source.originalFilename(), source.mediaType(), "es", source.checksum(), key,
            policyId, fingerprint, userId);
    }

    private SourceVersionRecord cloneVersion(
        UUID realmId, UUID documentId, UUID userId, String title, UUID policyId,
        String fingerprint, int number, SourceVersionRecord active
    ) {
        UUID versionId = UUID.randomUUID();
        String extension = active.mediaType().equals("text/plain") ? "txt" : "md";
        String key = realmId + "/" + documentId + "/" + versionId + "." + extension;
        return repository.createVersion(documentId, versionId, realmId, number, title,
            active.originalFilename(), active.mediaType(), active.language(), active.checksum(),
            key, policyId, fingerprint, userId);
    }

    private void authorize(UUID realmId, UUID policyId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, policyId, userId);
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            throw new InvalidSourceException("The title must contain between 1 and 200 characters.");
        }
        return title.strip();
    }
}
