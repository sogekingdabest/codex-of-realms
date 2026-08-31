package dev.codexofrealms.content.application.ingestion;

public record SourceChunk(int ordinal, String heading, String content, int startOffset, int endOffset) {
}
