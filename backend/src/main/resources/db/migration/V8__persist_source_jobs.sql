CREATE TABLE source_job (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id),
    document_id UUID NOT NULL,
    version_id UUID NOT NULL,
    requested_by UUID NOT NULL REFERENCES codex_user(id),
    submitted_by UUID NOT NULL REFERENCES codex_user(id),
    idempotency_key VARCHAR(128),
    request_hash CHAR(64) NOT NULL,
    pipeline_config TEXT NOT NULL,
    no_op BOOLEAN NOT NULL DEFAULT FALSE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('UPLOADING','QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    run_attempts INTEGER NOT NULL DEFAULT 0 CHECK (run_attempts BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_chunks INTEGER NOT NULL DEFAULT 0,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(40),
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (realm_id, document_id) REFERENCES source_document(realm_id, id),
    FOREIGN KEY (realm_id, version_id) REFERENCES document_version(realm_id, id),
    UNIQUE (realm_id, submitted_by, idempotency_key),
    CHECK ((state = 'RUNNING') = (lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)),
    CHECK (completed_chunks >= 0 AND total_chunks >= completed_chunks)
);
CREATE UNIQUE INDEX uk_source_job_version ON source_job(version_id) WHERE NOT no_op;
CREATE INDEX idx_source_job_queue ON source_job(next_attempt_at, created_at) WHERE state='QUEUED';
CREATE INDEX idx_source_job_realm ON source_job(realm_id, created_at DESC);
CREATE TABLE source_job_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES source_job(id),
    state VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL,
    error_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Preserve interrupted versions and make their recovery visible. Their unknown
-- processing configuration requires a new version using the current pipeline.
INSERT INTO source_job (id, realm_id, document_id, version_id, requested_by, submitted_by,
    request_hash, pipeline_config, state, error_code)
SELECT gen_random_uuid(), v.realm_id, v.document_id, v.id, v.created_by, v.created_by,
    v.checksum_sha256, 'legacy', 'FAILED', 'PROCESSING_INTERRUPTED'
FROM document_version v JOIN source_document d ON d.id=v.document_id
WHERE d.active AND v.processing_status IN ('RECEIVED','VALIDATED','PROCESSING','FAILED');
UPDATE document_version SET processing_status='FAILED', failure_code='PROCESSING_INTERRUPTED'
WHERE processing_status IN ('RECEIVED','VALIDATED','PROCESSING');
INSERT INTO source_job_event(job_id,state,attempt,error_code)
SELECT id,state,attempts,error_code FROM source_job;
