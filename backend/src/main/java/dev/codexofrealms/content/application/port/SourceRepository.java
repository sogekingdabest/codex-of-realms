package dev.codexofrealms.content.application.port;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.application.ingestion.SourceChunk;
import dev.codexofrealms.content.application.source.SourceChunkView;
import dev.codexofrealms.content.application.source.SourceDocumentView;
import dev.codexofrealms.content.domain.ProcessingStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository {

    void createDocument(UUID id, UUID realmId, String title, UUID userId);

    SourceVersion createVersion(UUID realmId, UUID userId, SourceVersion version);

    int nextVersionNumber(UUID realmId, UUID documentId);

    Optional<SourceVersion> findActiveVersion(UUID realmId, UUID documentId);

    Optional<SourceVersion> findLatestVersion(UUID realmId, UUID documentId);

    Optional<SourceEvidence> findActiveEvidence(UUID realmId, UUID accessPolicyId, UUID chunkId);

    List<SourceChunkView> listActiveChunks(UUID realmId, UUID documentId);

    void setStatus(UUID versionId, ProcessingStatus status, String failureCode);

    void activate(
        SourceVersion version,
        List<SourceChunk> chunks,
        List<float[]> embeddings,
        String provider,
        String model
    );

    List<SourceDocumentView> listAccessible(UUID realmId, UUID userId);

    Optional<SourceDocumentView> findActiveView(UUID realmId, UUID documentId);

    Optional<SourceDocumentView> findAccessibleView(UUID realmId, UUID documentId, UUID userId);

    Optional<SourceVersion> findAccessibleVersion(
        UUID realmId,
        UUID documentId,
        UUID versionId,
        UUID userId
    );

    List<String> retireDocument(UUID realmId, UUID documentId);
}
