package dev.codexofrealms.content.application.port;

import dev.codexofrealms.content.application.ingestion.SourceJobView;

import java.util.UUID;

public record SourceJob(
        SourceJobView view,
        UUID realmId,
        UUID requestedBy,
        String requestHash,
        String pipelineConfig,
        SourceVersion version,
        UUID leaseToken) {}
