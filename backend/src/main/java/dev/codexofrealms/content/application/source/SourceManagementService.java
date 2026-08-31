package dev.codexofrealms.content.application.source;

import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceManagementService {
    private final SourceMetadataCoordinator metadata;
    private final RawSourceStorage storage;

    SourceManagementService(SourceMetadataCoordinator metadata, RawSourceStorage storage) {
        this.metadata = metadata;
        this.storage = storage;
    }

    public List<SourceDocumentView> list(UUID realmId, UUID userId) {
        return metadata.list(realmId, userId);
    }

    public SourceDocumentView get(UUID realmId, UUID documentId, UUID userId) {
        return metadata.get(realmId, documentId, userId);
    }

    public List<SourceChunkView> chunks(UUID realmId, UUID documentId, UUID userId) {
        return metadata.chunks(realmId, documentId, userId);
    }

    public SourceContentView content(UUID realmId, UUID documentId, UUID versionId, UUID userId) {
        SourceVersion version = metadata.accessibleVersion(realmId, documentId, versionId, userId);
        byte[] bytes = storage.read(version.storageKey());
        return new SourceContentView(
            documentId,
            versionId,
            version.title(),
            version.originalFilename(),
            new String(bytes, StandardCharsets.UTF_8)
        );
    }

    public void delete(UUID realmId, UUID documentId, UUID userId) {
        metadata.retire(realmId, documentId, userId).forEach(storage::delete);
    }
}
