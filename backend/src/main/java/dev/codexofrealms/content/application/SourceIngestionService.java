package dev.codexofrealms.content.application;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.content.domain.ProcessingStatus;
import dev.codexofrealms.content.infrastructure.SourceVersionRecord;
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
    private final SourceMetadataCoordinator metadata;
    private final ObjectProvider<TextEmbedding> embeddingProvider;
    private final IngestionProperties properties;

    SourceIngestionService(
        SourceFileValidator validator, StructuralChunker chunker, RawSourceStorage storage,
        SourceMetadataCoordinator metadata, ObjectProvider<TextEmbedding> embeddingProvider,
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
        SourceVersionRecord active = metadata.activeVersion(realmId, documentId, userId);
        PreparedVersion prepared = metadata.reprocess(realmId, documentId, userId, fingerprint());
        if (prepared.isUnchanged()) return prepared.existing();
        SourceVersionRecord version = prepared.version();
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

    public List<SourceDocumentView> list(UUID realmId, UUID userId) {
        return metadata.list(realmId, userId);
    }

    public SourceDocumentView get(UUID realmId, UUID documentId, UUID userId) {
        return metadata.get(realmId, documentId, userId);
    }

    public void delete(UUID realmId, UUID documentId, UUID userId) {
        metadata.retire(realmId, documentId, userId).forEach(storage::delete);
    }

    private SourceDocumentView process(UUID realmId, UUID userId, SourceVersionRecord version, AcceptedSource source) {
        try {
            storage.write(version.storageKey(), source.bytes());
            return processAfterStorage(realmId, userId, version, source);
        } catch (RuntimeException exception) {
            metadata.status(version.versionId(), ProcessingStatus.FAILED, failureCode(exception));
            throw exception;
        }
    }

    private SourceDocumentView processAfterStorage(UUID realmId, UUID userId, SourceVersionRecord version, AcceptedSource source) {
        metadata.status(version.versionId(), ProcessingStatus.VALIDATED, null);
        List<SourceChunk> chunks = chunker.split(source.text());
        metadata.status(version.versionId(), ProcessingStatus.PROCESSING, null);
        TextEmbedding generator = embeddingProvider.getIfAvailable(() -> {
            throw new EmbeddingUnavailableException("No embedding model is configured.");
        });
        List<float[]> embeddings = embedInBatches(generator, chunks);
        EmbeddingDescriptor descriptor = generator.descriptor();
        return metadata.activate(realmId, userId, version, chunks, embeddings,
            descriptor.provider(), descriptor.model());
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
        if (exception instanceof EmbeddingUnavailableException) return "EMBEDDING_UNAVAILABLE";
        if (exception instanceof InvalidSourceException) return "INVALID_SOURCE";
        return "PROCESSING_ERROR";
    }
}
