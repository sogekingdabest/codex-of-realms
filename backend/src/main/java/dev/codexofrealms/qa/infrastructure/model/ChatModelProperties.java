package dev.codexofrealms.qa.infrastructure.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codex.qa")
public record ChatModelProperties(
    String chatProvider,
    String chatModel,
    int chatContextSize,
    int chatMaxPredictTokens,
    String chatKeepAlive
) {
    public ChatModelProperties {
        if (chatProvider == null || chatProvider.isBlank() || chatModel == null || chatModel.isBlank()) {
            throw new IllegalArgumentException("QA model provenance is required.");
        }
        if (chatContextSize < 512 || chatContextSize > 131072
            || chatMaxPredictTokens < 1 || chatMaxPredictTokens > 4096) {
            throw new IllegalArgumentException("QA model token limits are invalid.");
        }
        if (chatKeepAlive == null || chatKeepAlive.isBlank()) {
            throw new IllegalArgumentException("QA model keep-alive is required.");
        }
    }
}
