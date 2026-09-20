package dev.codexofrealms.content.application.port;

import dev.codexofrealms.content.application.ingestion.*;

import java.util.*;

public interface SourceJobRepository {
    void serializeSubmission(UUID realmId, UUID userId, String key);

    Optional<SourceJob> findSubmission(UUID realmId, UUID userId, String key);

    SourceJob insert(
            UUID realmId,
            UUID userId,
            SourceVersion version,
            String key,
            String hash,
            String config,
            boolean noOp);

    Optional<SourceJob> find(UUID realmId, UUID jobId);

    List<SourceJobView> list(UUID realmId);

    List<SourceJob> interruptedUploads();

    Optional<SourceJob> claim();

    void recoverExpired();

    boolean renew(SourceJob job);

    boolean progress(SourceJob job, int completed, int total);

    boolean queue(UUID jobId);

    boolean lockCurrent(SourceJob job);

    Optional<SourceJob> lock(UUID realmId, UUID jobId);

    void succeeded(SourceJob job);

    void failed(SourceJob job, String error, boolean transientFailure);

    void uploadFailed(UUID jobId, String error);

    void retry(SourceJob job, UUID userId);

    long pendingCount();
}
