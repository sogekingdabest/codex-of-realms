CREATE TABLE source_document (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES codex_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_source_document_scope UNIQUE (realm_id, id),
    CONSTRAINT ck_source_document_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE INDEX idx_source_document_realm_active
    ON source_document (realm_id, created_at, id)
    WHERE active;

CREATE TABLE document_version (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    language VARCHAR(16) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    processing_status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(40),
    access_policy_id UUID NOT NULL,
    embedding_provider VARCHAR(80),
    embedding_model VARCHAR(160),
    embedding_dimension INTEGER,
    pipeline_fingerprint CHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES codex_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_document_version_number UNIQUE (document_id, version_number),
    CONSTRAINT uk_document_version_scope UNIQUE (realm_id, id),
    CONSTRAINT fk_document_version_document
        FOREIGN KEY (realm_id, document_id)
        REFERENCES source_document (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_version_policy
        FOREIGN KEY (realm_id, access_policy_id)
        REFERENCES access_policy (realm_id, id),
    CONSTRAINT ck_document_version_number CHECK (version_number > 0),
    CONSTRAINT ck_document_version_checksum CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_version_status
        CHECK (processing_status IN ('RECEIVED', 'VALIDATED', 'PROCESSING', 'READY', 'FAILED', 'RETIRED')),
    CONSTRAINT ck_document_version_active_ready CHECK (NOT active OR processing_status = 'READY'),
    CONSTRAINT ck_document_version_embedding_dimension CHECK (embedding_dimension IS NULL OR embedding_dimension > 0)
);

CREATE UNIQUE INDEX uk_document_version_one_active
    ON document_version (document_id)
    WHERE active;

CREATE INDEX idx_document_version_document
    ON document_version (realm_id, document_id, version_number DESC);

CREATE TABLE lore_chunk (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    heading VARCHAR(500),
    content TEXT NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    embedding VECTOR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lore_chunk_ordinal UNIQUE (document_version_id, ordinal),
    CONSTRAINT uk_lore_chunk_scope UNIQUE (realm_id, id),
    CONSTRAINT fk_lore_chunk_version
        FOREIGN KEY (realm_id, document_version_id)
        REFERENCES document_version (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_lore_chunk_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_lore_chunk_content_not_blank CHECK (length(btrim(content)) > 0),
    CONSTRAINT ck_lore_chunk_offsets CHECK (start_offset >= 0 AND end_offset > start_offset)
);

CREATE INDEX idx_lore_chunk_version
    ON lore_chunk (realm_id, document_version_id, ordinal);
