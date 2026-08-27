package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.content.TextEmbedding;
import dev.codexofrealms.content.application.EmbeddingUnavailableException;
import dev.codexofrealms.content.application.IngestionProperties;
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
            throw new EmbeddingUnavailableException("No embedding model is configured.");
        });
        return model.embed(texts);
    }

    @Override
    public EmbeddingDescriptor descriptor() {
        return descriptor;
    }
}
