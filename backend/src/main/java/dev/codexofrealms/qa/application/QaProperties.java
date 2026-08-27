package dev.codexofrealms.qa.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codex.qa")
public record QaProperties(
    int retrievalLimit,
    int maxEvidenceChunks,
    double minimumSimilarity,
    double minimumQuestionCoverage,
    double minimumClaimCoverage,
    int maxClaims,
    int maxAnswerCharacters,
    String chatProvider,
    String chatModel
) {
    public QaProperties {
        if (retrievalLimit < 1 || retrievalLimit > 20) {
            throw new IllegalArgumentException("QA retrieval limit must be between 1 and 20.");
        }
        if (maxEvidenceChunks < 1 || maxEvidenceChunks > retrievalLimit) {
            throw new IllegalArgumentException("QA evidence limit is invalid.");
        }
        requireProbability(minimumSimilarity, "minimum similarity");
        requireProbability(minimumQuestionCoverage, "minimum question coverage");
        requireProbability(minimumClaimCoverage, "minimum claim coverage");
        if (maxClaims < 1 || maxClaims > 10 || maxAnswerCharacters < 100 || maxAnswerCharacters > 10000) {
            throw new IllegalArgumentException("QA output limits are invalid.");
        }
        if (chatProvider == null || chatProvider.isBlank() || chatModel == null || chatModel.isBlank()) {
            throw new IllegalArgumentException("QA model provenance is required.");
        }
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("QA " + name + " must be between 0 and 1.");
        }
    }
}
