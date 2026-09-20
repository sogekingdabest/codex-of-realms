package dev.codexofrealms.content.application.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SourceJobView(
        UUID id,
        UUID documentId,
        UUID versionId,
        int versionNumber,
        String title,
        String originalFilename,
        UUID accessPolicyId,
        SourceJobState state,
        int attempts,
        int completedChunks,
        int totalChunks,
        String errorCode,
        boolean noOp,
        Instant nextAttemptAt,
        Instant createdAt,
        List<SourceJobEvent> history) {}
