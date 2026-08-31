package dev.codexofrealms.content.application.source;

import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.content.application.port.SourceVersion;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SourceMetadataCoordinator {
    private final RealmAccess realmAccess;
    private final SourceRepository repository;

    SourceMetadataCoordinator(RealmAccess realmAccess, SourceRepository repository) {
        this.realmAccess = realmAccess;
        this.repository = repository;
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
            .orElseThrow(SourceException::unavailable);
    }

    @Transactional(readOnly = true)
    List<SourceChunkView> chunks(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        repository.findActiveView(realmId, documentId)
            .orElseThrow(SourceException::unavailable);
        return repository.listActiveChunks(realmId, documentId);
    }

    @Transactional(readOnly = true)
    SourceVersion accessibleVersion(UUID realmId, UUID documentId, UUID versionId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleVersion(realmId, documentId, versionId, userId)
            .orElseThrow(SourceException::unavailable);
    }

    @Transactional
    List<String> retire(UUID realmId, UUID documentId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        return repository.retireDocument(realmId, documentId);
    }
}
