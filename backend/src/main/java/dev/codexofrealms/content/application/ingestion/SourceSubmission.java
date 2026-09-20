package dev.codexofrealms.content.application.ingestion;

import java.util.UUID;

public record SourceSubmission(SourceJobView job, UUID documentId, UUID versionId, int excludedSentences) {
    public SourceSubmission(SourceJobView job, int excludedSentences) {
        this(job, job.documentId(), job.versionId(), excludedSentences);
    }
    public SourceSubmission(SourceJobView job) {
        this(job, job.documentId(), job.versionId(), 0);
    }
}
