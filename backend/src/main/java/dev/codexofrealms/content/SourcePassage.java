package dev.codexofrealms.content;

/** Literal paragraph coordinates use the same UTF-16 offsets as Java and the browser. */
public record SourcePassage(String content, int startOffset, int endOffset) {}
