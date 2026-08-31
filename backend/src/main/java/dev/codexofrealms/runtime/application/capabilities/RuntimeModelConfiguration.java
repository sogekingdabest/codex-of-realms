package dev.codexofrealms.runtime.application.capabilities;

import java.util.Locale;
import java.util.Objects;

public record RuntimeModelConfiguration(ConfiguredModel chat, ConfiguredModel embedding) {

    public RuntimeModelConfiguration {
        chat = Objects.requireNonNull(chat, "Chat model configuration is required.");
        embedding = Objects.requireNonNull(embedding, "Embedding model configuration is required.");
    }

    public static RuntimeModelConfiguration of(
        String chatProvider,
        String chatModel,
        String embeddingProvider,
        String embeddingModel
    ) {
        return new RuntimeModelConfiguration(
            new ConfiguredModel(chatProvider, chatModel),
            new ConfiguredModel(embeddingProvider, embeddingModel)
        );
    }

    public record ConfiguredModel(String provider, String model) {

        public ConfiguredModel {
            provider = normalizeProvider(provider);
            model = model == null ? "" : model.trim();
        }

        boolean usesOllama() {
            return "ollama".equals(provider) && !model.isBlank();
        }

        private static String normalizeProvider(String provider) {
            return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        }
    }
}
