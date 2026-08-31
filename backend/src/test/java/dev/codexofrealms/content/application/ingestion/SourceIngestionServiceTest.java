package dev.codexofrealms.content.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceVersion;
import dev.codexofrealms.content.application.source.SourceDocumentView;
import dev.codexofrealms.content.domain.ProcessingStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@SuppressWarnings("unchecked")
class SourceIngestionServiceTest {
    private final RawSourceStorage storage = mock(RawSourceStorage.class);
    private final IngestionMetadataCoordinator metadata = mock(IngestionMetadataCoordinator.class);
    private final ObjectProvider<TextEmbedding> embeddingProvider = mock(ObjectProvider.class);
    private final TextEmbedding embedding = mock(TextEmbedding.class);
    private final IngestionProperties properties = new IngestionProperties(20_000, 200, 20, 1, "test", "model");
    private final SourceIngestionService service = new SourceIngestionService(
        new SourceFileValidator(properties), new StructuralChunker(properties), storage,
        metadata, embeddingProvider, properties
    );
    private final UUID realmId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private final SourceVersion version = version();
    private final SourceDocumentView view = view(version);

    @BeforeEach
    void configureEmbedding() {
        when(embeddingProvider.getIfAvailable(any())).thenReturn(embedding);
        when(embedding.descriptor()).thenReturn(new EmbeddingDescriptor("test", "model"));
        when(embedding.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(ignored -> new float[] {1f, 2f}).toList();
        });
        when(metadata.activate(eq(realmId), eq(userId), eq(version), anyList(), anyList(), eq("test"), eq("model")))
            .thenReturn(view);
    }

    @Test
    void createsStoresEmbedsInBatchesAndActivates() {
        when(metadata.create(eq(realmId), eq(userId), eq("Crónica"), eq(policyId), any(), any()))
            .thenReturn(PreparedVersion.created(version));
        byte[] bytes = ("# Uno\n\n" + "Texto largo. ".repeat(50)).getBytes(StandardCharsets.UTF_8);

        SourceDocumentView result = service.create(
            realmId, userId, "Crónica", policyId, bytes, "cronica.md", "text/markdown"
        );

        assertThat(result).isEqualTo(view);
        verify(storage).write(version.storageKey(), bytes);
        verify(metadata).status(version.versionId(), ProcessingStatus.VALIDATED, null);
        verify(metadata).status(version.versionId(), ProcessingStatus.PROCESSING, null);
        verify(embedding, times(4)).embed(anyList());
        verify(metadata).activate(eq(realmId), eq(userId), eq(version), anyList(), anyList(), eq("test"), eq("model"));
    }

    @Test
    void unchangedReplacementSkipsStorageAndEmbeddings() {
        when(metadata.replace(eq(realmId), eq(version.documentId()), eq(userId), eq(policyId), any(), any()))
            .thenReturn(PreparedVersion.unchanged(view));

        SourceDocumentView result = service.replace(
            realmId, version.documentId(), userId, policyId,
            "Sin cambios".getBytes(StandardCharsets.UTF_8), "cronica.md", "text/markdown"
        );

        assertThat(result).isEqualTo(view);
        verify(storage, never()).write(any(), any());
        verify(embeddingProvider, never()).getIfAvailable(any());
    }

    @Test
    void unchangedReprocessSkipsRawReadAndEmbeddings() {
        when(metadata.activeVersion(realmId, version.documentId(), userId)).thenReturn(version);
        when(metadata.reprocess(eq(realmId), eq(version.documentId()), eq(userId), any()))
            .thenReturn(PreparedVersion.unchanged(view));

        SourceDocumentView result = service.reprocess(realmId, version.documentId(), userId);

        assertThat(result).isEqualTo(view);
        verify(storage, never()).read(any());
        verify(embeddingProvider, never()).getIfAvailable(any());
    }

    @Test
    void marksVersionFailedWhenStorageFails() {
        when(metadata.create(eq(realmId), eq(userId), eq("Crónica"), eq(policyId), any(), any()))
            .thenReturn(PreparedVersion.created(version));
        doThrow(new IllegalStateException("disk unavailable"))
            .when(storage).write(eq(version.storageKey()), any());

        assertThatThrownBy(() -> service.create(
            realmId, userId, "Crónica", policyId,
            "Contenido".getBytes(StandardCharsets.UTF_8), "cronica.md", "text/markdown"
        )).isInstanceOf(IllegalStateException.class);

        verify(metadata).status(version.versionId(), ProcessingStatus.FAILED, "PROCESSING_ERROR");
    }

    @Test
    void marksVersionFailedWithEmbeddingCode() {
        when(metadata.create(eq(realmId), eq(userId), eq("Crónica"), eq(policyId), any(), any()))
            .thenReturn(PreparedVersion.created(version));
        when(embedding.embed(anyList())).thenThrow(
            IngestionException.embeddingUnavailable("embedding unavailable")
        );

        assertThatThrownBy(() -> service.create(
            realmId, userId, "Crónica", policyId,
            "Contenido".getBytes(StandardCharsets.UTF_8), "cronica.md", "text/markdown"
        )).isInstanceOfSatisfying(IngestionException.class,
            exception -> assertThat(exception.code()).isEqualTo(IngestionException.Code.EMBEDDING_UNAVAILABLE));

        verify(metadata).status(version.versionId(), ProcessingStatus.FAILED, "EMBEDDING_UNAVAILABLE");
    }

    private SourceVersion version() {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        return new SourceVersion(
            documentId, versionId, 1, "Crónica", "cronica.md", "text/markdown", "es",
            "checksum", realmId + "/" + documentId + "/" + versionId + ".md", policyId, "fingerprint"
        );
    }

    private SourceDocumentView view(SourceVersion sourceVersion) {
        return new SourceDocumentView(
            sourceVersion.documentId(), realmId, sourceVersion.title(), sourceVersion.versionId(), 1,
            sourceVersion.checksum(), sourceVersion.originalFilename(), sourceVersion.mediaType(), "es",
            ProcessingStatus.READY, policyId, "test", "model", 2, 1, Instant.EPOCH
        );
    }
}
