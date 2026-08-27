package dev.codexofrealms.content;

public record EmbeddingDescriptor(String provider, String model) {

    public EmbeddingDescriptor {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding provider and model are required.");
        }
        provider = provider.strip();
        model = model.strip();
    }
}
