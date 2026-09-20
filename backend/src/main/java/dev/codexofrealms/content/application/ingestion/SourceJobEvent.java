package dev.codexofrealms.content.application.ingestion;

import java.time.Instant;

public record SourceJobEvent(
        SourceJobState state, int attempt, String errorCode, Instant createdAt) {}
