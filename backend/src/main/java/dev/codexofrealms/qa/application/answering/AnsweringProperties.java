package dev.codexofrealms.qa.application.answering;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codex.qa")
public record AnsweringProperties(
    int retrievalLimit,
    int maxEvidenceChunks,
    double minimumSimilarity,
    double minimumQuestionCoverage,
    int maxExcerpts,
    int maxAnswerCharacters
) {
    public AnsweringProperties {
        if (retrievalLimit < 1 || retrievalLimit > 20) {
            throw new IllegalArgumentException("QA retrieval limit must be between 1 and 20.");
        }
        if (maxEvidenceChunks < 1 || maxEvidenceChunks > retrievalLimit || maxEvidenceChunks > 6) {
            throw new IllegalArgumentException("QA evidence limit is invalid.");
        }
        requireProbability(minimumSimilarity, "minimum similarity");
        requireProbability(minimumQuestionCoverage, "minimum question coverage");
        if (maxExcerpts < 1 || maxExcerpts > 3 || maxAnswerCharacters < 100 || maxAnswerCharacters > 6000) {
            throw new IllegalArgumentException("QA output limits are invalid.");
        }
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("QA " + name + " must be between 0 and 1.");
        }
    }
}
