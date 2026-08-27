package dev.codexofrealms.content;

import java.util.List;

public interface TextEmbedding {

    List<float[]> embed(List<String> texts);

    EmbeddingDescriptor descriptor();

    default float[] embed(String text) {
        List<float[]> result = embed(List.of(text));
        if (result.size() != 1) {
            throw new IllegalStateException("The embedding provider returned an unexpected result count.");
        }
        return result.getFirst();
    }
}
