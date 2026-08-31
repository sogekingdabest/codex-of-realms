package dev.codexofrealms.content.application.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceManagementServiceTest {
    private final SourceMetadataCoordinator metadata = mock(SourceMetadataCoordinator.class);
    private final RawSourceStorage storage = mock(RawSourceStorage.class);
    private final SourceManagementService service = new SourceManagementService(metadata, storage);

    @Test
    void readsContentAfterResolvingAccessibleMetadata() {
        UUID realmId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SourceVersion version = new SourceVersion(
            documentId, versionId, 1, "Crónica", "cronica.md", "text/markdown", "es",
            "checksum", "storage-key", UUID.randomUUID(), "fingerprint"
        );
        when(metadata.accessibleVersion(realmId, documentId, versionId, userId)).thenReturn(version);
        when(storage.read("storage-key")).thenReturn("Contenido ñ".getBytes(StandardCharsets.UTF_8));

        SourceContentView result = service.content(realmId, documentId, versionId, userId);

        assertThat(result.content()).isEqualTo("Contenido ñ");
        assertThat(result.title()).isEqualTo("Crónica");
    }

    @Test
    void retiresMetadataBeforeDeletingFilesAndDeletionIsDelegatedPerKey() {
        UUID realmId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(metadata.retire(realmId, documentId, userId)).thenReturn(List.of("one.md", "two.md"));

        service.delete(realmId, documentId, userId);

        verify(storage).delete("one.md");
        verify(storage).delete("two.md");
    }
}
