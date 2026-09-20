package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.content.application.ingestion.IngestionException;
import dev.codexofrealms.content.application.ingestion.IngestionProperties;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class SpringAiEmbeddingGenerator implements TextEmbedding {

    private final ObjectProvider<EmbeddingModel> modelProvider;
    private final EmbeddingDescriptor descriptor;

    SpringAiEmbeddingGenerator(
        ObjectProvider<EmbeddingModel> modelProvider,
        IngestionProperties properties
    ) {
        this.modelProvider = modelProvider;
        this.descriptor = new EmbeddingDescriptor(
            properties.embeddingProvider(), properties.embeddingModel()
        );
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingModel model = modelProvider.getIfAvailable(() -> {
            throw new dev.codexofrealms.content.application.ingestion.SourceJobException("PIPELINE_CHANGED", "No embedding model is configured.");
        });
        try { return model.embed(texts); }
        catch (RuntimeException exception) {
            for (Throwable cause=exception;cause!=null;cause=cause.getCause()) {
                if (cause instanceof org.springframework.web.client.ResourceAccessException
                    || cause instanceof org.springframework.ai.retry.TransientAiException
                    || cause instanceof org.springframework.web.client.RestClientResponseException response
                        && (response.getStatusCode().is5xxServerError() || response.getStatusCode().value()==429)) {
                    throw IngestionException.embeddingUnavailable("The embedding model is temporarily unavailable.");
                }
            }
            throw new dev.codexofrealms.content.application.ingestion.SourceJobException("PIPELINE_CHANGED", "The embedding request was rejected.");
        }
    }

    @Override
    public EmbeddingDescriptor descriptor() {
        return descriptor;
    }
}
