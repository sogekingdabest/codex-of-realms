package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.application.ingestion.*;
import dev.codexofrealms.content.application.port.*;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@Transactional
public class SourceJobJdbcRepository implements SourceJobRepository {
    private final JdbcClient jdbc;
    private static final String SELECT =
            """
            SELECT j.*, v.version_number, d.title, v.original_filename, v.media_type,
                v.language, v.checksum_sha256, v.storage_key, v.access_policy_id, v.pipeline_fingerprint
            FROM source_job j JOIN document_version v ON v.id=j.version_id
            JOIN source_document d ON d.id=j.document_id
            """;

    public SourceJobJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void serializeSubmission(UUID realmId, UUID userId, String key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", realmId + ":" + userId + ":" + key)
                .query((rs, row) -> true)
                .single();
    }

    public Optional<SourceJob> findSubmission(UUID realmId, UUID userId, String key) {
        return jdbc.sql(
                        SELECT
                                + " WHERE j.realm_id=:realm AND j.submitted_by=:user AND"
                                + " j.idempotency_key=:key")
                .param("realm", realmId)
                .param("user", userId)
                .param("key", key)
                .query(this::map)
                .optional();
    }

    public SourceJob insert(
            UUID realmId,
            UUID userId,
            SourceVersion version,
            String key,
            String hash,
            String config,
            boolean noOp) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO source_job(id,realm_id,document_id,version_id,requested_by,submitted_by,
                            idempotency_key,request_hash,pipeline_config,no_op,state)
                        VALUES (:id,:realm,:doc,:version,:user,:user,:key,:hash,:config,:noop,:state)
                        """)
                .param("id", id)
                .param("realm", realmId)
                .param("doc", version.documentId())
                .param("version", version.versionId())
                .param("user", userId)
                .param("key", key)
                .param("hash", hash)
                .param("config", config)
                .param("noop", noOp)
                .param("state", noOp ? "SUCCEEDED" : "UPLOADING")
                .update();
        event(id);
        return find(realmId, id).orElseThrow();
    }

    public Optional<SourceJob> find(UUID realmId, UUID jobId) {
        return jdbc.sql(SELECT + " WHERE j.realm_id=:realm AND j.id=:id")
                .param("realm", realmId)
                .param("id", jobId)
                .query(this::map)
                .optional();
    }

    public List<SourceJobView> list(UUID realmId) {
        return jdbc
                .sql(SELECT + " WHERE j.realm_id=:realm ORDER BY j.created_at DESC, j.id")
                .param("realm", realmId)
                .query(this::map)
                .list()
                .stream()
                .map(SourceJob::view)
                .toList();
    }

    public List<SourceJob> interruptedUploads() {
        return jdbc.sql(
                        SELECT
                                + " WHERE j.state='UPLOADING' AND j.updated_at < CURRENT_TIMESTAMP"
                                + " - INTERVAL '5 minutes' ORDER BY j.created_at LIMIT 20")
                .query(this::map)
                .list();
    }

    public Optional<SourceJob> claim() {
        Optional<UUID> id =
                jdbc.sql(
                                """
                                WITH candidate AS (
                                    SELECT id FROM source_job WHERE state='QUEUED' AND next_attempt_at<=CURRENT_TIMESTAMP
                                    ORDER BY next_attempt_at,created_at FOR UPDATE SKIP LOCKED LIMIT 1
                                ) UPDATE source_job SET state='RUNNING', attempts=attempts+1, run_attempts=run_attempts+1,
                                    completed_chunks=0,total_chunks=0, error_code=NULL, lease_token=gen_random_uuid(),
                                    lease_expires_at=clock_timestamp()+INTERVAL '5 minutes', updated_at=CURRENT_TIMESTAMP
                                WHERE id IN (SELECT id FROM candidate) RETURNING id
                                """)
                        .query(UUID.class)
                        .optional();
        return id.map(
                value -> {
                    event(value);
                    jdbc.sql(
                                    "UPDATE document_version SET"
                                        + " processing_status='PROCESSING',failure_code=NULL WHERE"
                                        + " id=(SELECT version_id FROM source_job WHERE id=:id) AND"
                                        + " NOT active AND processing_status<>'RETIRED'")
                            .param("id", value)
                            .update();
                    return jdbc.sql(SELECT + " WHERE j.id=:id")
                            .param("id", value)
                            .query(this::map)
                            .single();
                });
    }

    public void recoverExpired() {
        List<UUID> ids =
                jdbc.sql(
                                """
                                UPDATE source_job SET state=CASE WHEN run_attempts<3 THEN 'QUEUED' ELSE 'FAILED' END,
                                    error_code='WORKER_INTERRUPTED',lease_token=NULL,lease_expires_at=NULL,
                                    next_attempt_at=CURRENT_TIMESTAMP + CASE WHEN run_attempts=1 THEN INTERVAL '30 seconds' ELSE INTERVAL '2 minutes' END,
                                    updated_at=CURRENT_TIMESTAMP
                                WHERE state='RUNNING' AND lease_expires_at<=clock_timestamp() RETURNING id
                                """)
                        .query(UUID.class)
                        .list();
        ids.forEach(
                id -> {
                    event(id);
                    markVersionFailed(id);
                });
        // Retired documents and superseded versions can never publish.
        List<UUID> cancelled =
                jdbc.sql(
                                """
                                UPDATE source_job j SET state='CANCELLED',error_code='SOURCE_UNAVAILABLE',
                                    lease_token=NULL,lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP
                                WHERE j.state IN ('UPLOADING','QUEUED','RUNNING','FAILED') AND (
                                    NOT EXISTS (SELECT 1 FROM source_document d JOIN realm r ON r.id=d.realm_id WHERE d.id=j.document_id AND d.active AND r.active)
                                    OR EXISTS (SELECT 1 FROM document_version v JOIN document_version current ON current.id=j.version_id
                                        WHERE v.document_id=j.document_id AND v.version_number>current.version_number))
                                RETURNING j.id
                                """)
                        .query(UUID.class)
                        .list();
        cancelled.forEach(this::event);
    }

    public boolean renew(SourceJob job) {
        return owned(
                                "UPDATE source_job SET lease_expires_at=clock_timestamp()+INTERVAL"
                                    + " '5 minutes',updated_at=CURRENT_TIMESTAMP",
                                job)
                        .update()
                == 1;
    }

    public boolean progress(SourceJob job, int completed, int total) {
        return owned(
                                "UPDATE source_job SET"
                                    + " completed_chunks=:done,total_chunks=:total,updated_at=CURRENT_TIMESTAMP",
                                job)
                        .param("done", completed)
                        .param("total", total)
                        .update()
                == 1;
    }

    public boolean queue(UUID id) {
        int changed =
                jdbc.sql(
                                "UPDATE source_job SET"
                                    + " state='QUEUED',error_code=NULL,updated_at=CURRENT_TIMESTAMP"
                                    + " WHERE id=:id AND state='UPLOADING'")
                        .param("id", id)
                        .update();
        if (changed == 1) event(id);
        return changed == 1;
    }

    public Optional<SourceJob> lock(UUID realmId, UUID id) {
        return jdbc.sql(SELECT + " WHERE j.realm_id=:realm AND j.id=:id FOR UPDATE OF j")
                .param("realm", realmId)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public boolean lockCurrent(SourceJob job) {
        boolean exists =
                jdbc.sql(
                                "SELECT id FROM source_document WHERE id=:doc AND realm_id=:realm"
                                    + " AND active FOR UPDATE")
                        .param("doc", job.version().documentId())
                        .param("realm", job.realmId())
                        .query(UUID.class)
                        .optional()
                        .isPresent();
        Optional<SourceJob> current = lock(job.realmId(), job.view().id());
        if (!exists
                || current.isEmpty()
                || !Objects.equals(current.get().leaseToken(), job.leaseToken())) return false;
        return jdbc.sql(
                        """
                        SELECT EXISTS(SELECT 1 FROM source_job j WHERE j.id=:id AND j.state='RUNNING'
                            AND j.lease_token=:lease AND j.lease_expires_at>clock_timestamp()
                            AND NOT EXISTS (SELECT 1 FROM document_version v WHERE v.document_id=j.document_id AND v.version_number>:number))
                        """)
                .param("id", job.view().id())
                .param("lease", job.leaseToken())
                .param("number", job.version().versionNumber())
                .query(Boolean.class)
                .single();
    }

    public void succeeded(SourceJob job) {
        if (owned(
                                "UPDATE source_job SET"
                                    + " state='SUCCEEDED',error_code=NULL,completed_chunks=total_chunks,lease_token=NULL,lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP",
                                job)
                        .update()
                != 1) throw new IllegalStateException("Execution lease lost");
        event(job.view().id());
    }

    public void failed(SourceJob job, String error, boolean transientFailure) {
        int changed =
                owned(
                                """
                                UPDATE source_job SET state=CASE WHEN :transient AND run_attempts<3 THEN 'QUEUED' ELSE 'FAILED' END,
                                    error_code=:error,lease_token=NULL,lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP,
                                    next_attempt_at=CURRENT_TIMESTAMP + CASE WHEN run_attempts=1 THEN INTERVAL '30 seconds' ELSE INTERVAL '2 minutes' END
                                """,
                                job)
                        .param("transient", transientFailure)
                        .param("error", error)
                        .update();
        if (changed == 1) {
            event(job.view().id());
            markVersionFailed(job.view().id());
        }
    }

    public void uploadFailed(UUID id, String error) {
        if (jdbc.sql(
                                "UPDATE source_job SET"
                                    + " state='FAILED',error_code=:error,updated_at=CURRENT_TIMESTAMP"
                                    + " WHERE id=:id AND state='UPLOADING'")
                        .param("id", id)
                        .param("error", error)
                        .update()
                == 1) {
            event(id);
            markVersionFailed(id);
        }
    }

    public void retry(SourceJob job, UUID userId) {
        if (jdbc.sql(
                                """
                                UPDATE source_job SET state='QUEUED',requested_by=:user,run_attempts=0,
                                    completed_chunks=0,total_chunks=0,error_code=NULL,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                                WHERE id=:id AND state='FAILED'
                                """)
                        .param("id", job.view().id())
                        .param("user", userId)
                        .update()
                == 1) event(job.view().id());
    }

    public long pendingCount() {
        return jdbc.sql(
                        "SELECT count(*) FROM source_job WHERE state IN"
                            + " ('UPLOADING','QUEUED','RUNNING')")
                .query(Long.class)
                .single();
    }

    private JdbcClient.StatementSpec owned(String sql, SourceJob job) {
        return jdbc.sql(
                        sql
                                + " WHERE id=:id AND state='RUNNING' AND lease_token=:lease AND"
                                + " lease_expires_at>clock_timestamp()")
                .param("id", job.view().id())
                .param("lease", job.leaseToken());
    }

    private void event(UUID id) {
        jdbc.sql(
                        "INSERT INTO source_job_event(job_id,state,attempt,error_code) SELECT"
                            + " id,state,attempts,error_code FROM source_job WHERE id=:id")
                .param("id", id)
                .update();
    }

    private void markVersionFailed(UUID id) {
        jdbc.sql(
                        """
                        UPDATE document_version v SET processing_status='FAILED',failure_code=j.error_code
                        FROM source_job j WHERE j.id=:id AND v.id=j.version_id AND j.state='FAILED'
                            AND NOT v.active AND v.processing_status<>'RETIRED'
                        """)
                .param("id", id)
                .update();
    }

    private SourceJob map(ResultSet rs, int row) throws SQLException {
        UUID id = rs.getObject("id", UUID.class),
                doc = rs.getObject("document_id", UUID.class),
                version = rs.getObject("version_id", UUID.class);
        List<SourceJobEvent> history =
                jdbc.sql(
                                "SELECT state,attempt,error_code,created_at FROM source_job_event"
                                    + " WHERE job_id=:id ORDER BY id")
                        .param("id", id)
                        .query(
                                (r, n) ->
                                        new SourceJobEvent(
                                                SourceJobState.valueOf(r.getString("state")),
                                                r.getInt("attempt"),
                                                r.getString("error_code"),
                                                r.getTimestamp("created_at").toInstant()))
                        .list();
        SourceJobView view =
                new SourceJobView(
                        id,
                        doc,
                        version,
                        rs.getInt("version_number"),
                        rs.getString("title"),
                        rs.getString("original_filename"),
                        rs.getObject("access_policy_id", UUID.class),
                        SourceJobState.valueOf(rs.getString("state")),
                        rs.getInt("attempts"),
                        rs.getInt("completed_chunks"),
                        rs.getInt("total_chunks"),
                        rs.getString("error_code"),
                        rs.getBoolean("no_op"),
                        rs.getTimestamp("next_attempt_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        history);
        SourceVersion source =
                new SourceVersion(
                        doc,
                        version,
                        view.versionNumber(),
                        view.title(),
                        view.originalFilename(),
                        rs.getString("media_type"),
                        rs.getString("language"),
                        rs.getString("checksum_sha256"),
                        rs.getString("storage_key"),
                        view.accessPolicyId(),
                        rs.getString("pipeline_fingerprint"));
        return new SourceJob(
                view,
                rs.getObject("realm_id", UUID.class),
                rs.getObject("requested_by", UUID.class),
                rs.getString("request_hash"),
                rs.getString("pipeline_config"),
                source,
                rs.getObject("lease_token", UUID.class));
    }
}
