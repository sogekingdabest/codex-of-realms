package dev.codexofrealms.qa;

public record AnswerProvenance(
    String embeddingProvider,
    String embeddingModel,
    String chatProvider,
    String chatModel
) {
}
