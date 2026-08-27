package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.application.EmbeddingGenerator;
import dev.codexofrealms.content.application.EmbeddingUnavailableException;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class SpringAiEmbeddingGenerator implements EmbeddingGenerator {

    private final ObjectProvider<EmbeddingModel> modelProvider;

    SpringAiEmbeddingGenerator(ObjectProvider<EmbeddingModel> modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingModel model = modelProvider.getIfAvailable(() -> {
            throw new EmbeddingUnavailableException("No embedding model is configured.");
        });
        return model.embed(texts);
    }
}
