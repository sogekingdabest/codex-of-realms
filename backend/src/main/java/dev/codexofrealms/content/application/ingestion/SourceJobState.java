package dev.codexofrealms.content.application.ingestion;

public enum SourceJobState {
    UPLOADING,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
