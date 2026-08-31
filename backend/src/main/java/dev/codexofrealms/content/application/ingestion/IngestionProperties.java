package dev.codexofrealms.content.application.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codex.ingestion")
public record IngestionProperties(
    long maxFileBytes,
    int chunkMaxCharacters,
    int chunkOverlapCharacters,
    int embeddingBatchSize,
    String embeddingProvider,
    String embeddingModel
) {
    public IngestionProperties {
        if (maxFileBytes <= 0 || chunkMaxCharacters < 200) {
            throw new IllegalArgumentException("Invalid ingestion limits.");
        }
        if (chunkOverlapCharacters < 0 || chunkOverlapCharacters >= chunkMaxCharacters) {
            throw new IllegalArgumentException("Invalid chunk overlap.");
        }
        if (embeddingBatchSize < 1 || embeddingBatchSize > 128) {
            throw new IllegalArgumentException("Invalid embedding batch size.");
        }
    }
}
