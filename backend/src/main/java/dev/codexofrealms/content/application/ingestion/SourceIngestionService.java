package dev.codexofrealms.content.application.ingestion;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.content.application.port.RawSourceStorage;
import dev.codexofrealms.content.application.port.SourceVersion;
import dev.codexofrealms.content.application.source.SourceDocumentView;
import dev.codexofrealms.content.domain.ProcessingStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SourceIngestionService {
    private final SourceFileValidator validator;
    private final StructuralChunker chunker;
    private final RawSourceStorage storage;
    private final IngestionMetadataCoordinator metadata;
    private final ObjectProvider<TextEmbedding> embeddingProvider;
    private final IngestionProperties properties;

    SourceIngestionService(
        SourceFileValidator validator, StructuralChunker chunker, RawSourceStorage storage,
        IngestionMetadataCoordinator metadata, ObjectProvider<TextEmbedding> embeddingProvider,
        IngestionProperties properties
    ) {
        this.validator = validator;
        this.chunker = chunker;
        this.storage = storage;
        this.metadata = metadata;
        this.embeddingProvider = embeddingProvider;
        this.properties = properties;
    }

    public SourceDocumentView create(
        UUID realmId, UUID userId, String title, UUID policyId,
        byte[] bytes, String filename, String contentType
    ) {
        AcceptedSource source = validator.validate(bytes, filename, contentType);
        PreparedVersion prepared = metadata.create(realmId, userId, title, policyId, source, fingerprint());
        return process(realmId, userId, prepared.version(), source);
    }

    public SourceDocumentView replace(
        UUID realmId, UUID documentId, UUID userId, UUID policyId,
        byte[] bytes, String filename, String contentType
    ) {
        AcceptedSource source = validator.validate(bytes, filename, contentType);
        PreparedVersion prepared = metadata.replace(realmId, documentId, userId, policyId, source, fingerprint());
        return prepared.isUnchanged() ? prepared.existing() : process(realmId, userId, prepared.version(), source);
    }

    public SourceDocumentView reprocess(UUID realmId, UUID documentId, UUID userId) {
        SourceVersion active = metadata.activeVersion(realmId, documentId, userId);
        PreparedVersion prepared = metadata.reprocess(realmId, documentId, userId, fingerprint());
        if (prepared.isUnchanged()) return prepared.existing();
        SourceVersion version = prepared.version();
        AcceptedSource source;
        try {
            byte[] bytes = storage.read(active.storageKey());
            source = validator.validate(bytes, version.originalFilename(), version.mediaType());
        } catch (RuntimeException exception) {
            metadata.status(version.versionId(), ProcessingStatus.FAILED, failureCode(exception));
            throw exception;
        }
        return process(realmId, userId, version, source);
    }

    private SourceDocumentView process(UUID realmId, UUID userId, SourceVersion version, AcceptedSource source) {
        try {
            storage.write(version.storageKey(), source.bytes());
            metadata.status(version.versionId(), ProcessingStatus.VALIDATED, null);
            List<SourceChunk> chunks = chunker.split(source.text());
            metadata.status(version.versionId(), ProcessingStatus.PROCESSING, null);
            TextEmbedding generator = embeddingProvider.getIfAvailable(() -> {
                throw IngestionException.embeddingUnavailable("No embedding model is configured.");
            });
            List<float[]> embeddings = embedInBatches(generator, chunks);
            EmbeddingDescriptor descriptor = generator.descriptor();
            return metadata.activate(realmId, userId, version, chunks, embeddings,
                descriptor.provider(), descriptor.model());
        } catch (RuntimeException exception) {
            metadata.status(version.versionId(), ProcessingStatus.FAILED, failureCode(exception));
            throw exception;
        }
    }

    private String fingerprint() {
        String value = "structural-v1|" + properties.chunkMaxCharacters() + "|"
            + properties.chunkOverlapCharacters() + "|" + properties.embeddingProvider()
            + "|" + properties.embeddingModel();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<float[]> embedInBatches(TextEmbedding generator, List<SourceChunk> chunks) {
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += properties.embeddingBatchSize()) {
            int end = Math.min(start + properties.embeddingBatchSize(), chunks.size());
            embeddings.addAll(generator.embed(chunks.subList(start, end).stream()
                .map(SourceChunk::content).toList()));
        }
        return embeddings;
    }

    private static String failureCode(RuntimeException exception) {
        if (exception instanceof IngestionException ingestionException) {
            return switch (ingestionException.code()) {
                case EMBEDDING_UNAVAILABLE -> "EMBEDDING_UNAVAILABLE";
                case INVALID_SOURCE -> "INVALID_SOURCE";
            };
        }
        return "PROCESSING_ERROR";
    }
}
