CREATE TABLE lore_entity (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    entity_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    canon_status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    access_policy_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES codex_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL REFERENCES codex_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    promoted_by UUID REFERENCES codex_user(id),
    promoted_at TIMESTAMPTZ,
    CONSTRAINT uk_lore_entity_scope UNIQUE (realm_id, id),
    CONSTRAINT fk_lore_entity_policy
        FOREIGN KEY (realm_id, access_policy_id)
        REFERENCES access_policy (realm_id, id),
    CONSTRAINT ck_lore_entity_type
        CHECK (entity_type IN ('CHARACTER', 'PLACE', 'FACTION', 'OBJECT', 'EVENT')),
    CONSTRAINT ck_lore_entity_name
        CHECK (length(btrim(display_name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_lore_entity_description
        CHECK (length(description) <= 4000),
    CONSTRAINT ck_lore_entity_canon_status
        CHECK (canon_status IN ('PROPOSED', 'CANON')),
    CONSTRAINT ck_lore_entity_promotion
        CHECK (
            (canon_status = 'PROPOSED' AND promoted_by IS NULL AND promoted_at IS NULL)
            OR (canon_status = 'CANON' AND promoted_by IS NOT NULL AND promoted_at IS NOT NULL)
        )
);

CREATE INDEX idx_lore_entity_realm_active
    ON lore_entity (realm_id, entity_type, canon_status, lower(display_name), id)
    WHERE active;

CREATE TABLE lore_entity_alias (
    realm_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    alias VARCHAR(120) NOT NULL,
    alias_key VARCHAR(120) NOT NULL,
    PRIMARY KEY (entity_id, ordinal),
    CONSTRAINT uk_lore_entity_alias UNIQUE (entity_id, alias_key),
    CONSTRAINT fk_lore_entity_alias_entity
        FOREIGN KEY (realm_id, entity_id)
        REFERENCES lore_entity (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_lore_entity_alias_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_lore_entity_alias_value CHECK (length(btrim(alias)) BETWEEN 1 AND 120),
    CONSTRAINT ck_lore_entity_alias_key CHECK (length(btrim(alias_key)) BETWEEN 1 AND 120)
);

CREATE TABLE lore_relation (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    source_entity_id UUID NOT NULL,
    target_entity_id UUID NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    canon_status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    access_policy_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES codex_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL REFERENCES codex_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    promoted_by UUID REFERENCES codex_user(id),
    promoted_at TIMESTAMPTZ,
    CONSTRAINT uk_lore_relation_scope UNIQUE (realm_id, id),
    CONSTRAINT fk_lore_relation_source
        FOREIGN KEY (realm_id, source_entity_id)
        REFERENCES lore_entity (realm_id, id),
    CONSTRAINT fk_lore_relation_target
        FOREIGN KEY (realm_id, target_entity_id)
        REFERENCES lore_entity (realm_id, id),
    CONSTRAINT fk_lore_relation_policy
        FOREIGN KEY (realm_id, access_policy_id)
        REFERENCES access_policy (realm_id, id),
    CONSTRAINT ck_lore_relation_endpoints CHECK (source_entity_id <> target_entity_id),
    CONSTRAINT ck_lore_relation_type
        CHECK (relation_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT ck_lore_relation_description CHECK (length(description) <= 2000),
    CONSTRAINT ck_lore_relation_canon_status
        CHECK (canon_status IN ('PROPOSED', 'CANON')),
    CONSTRAINT ck_lore_relation_promotion
        CHECK (
            (canon_status = 'PROPOSED' AND promoted_by IS NULL AND promoted_at IS NULL)
            OR (canon_status = 'CANON' AND promoted_by IS NOT NULL AND promoted_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uk_lore_relation_active_claim
    ON lore_relation (realm_id, source_entity_id, relation_type, target_entity_id)
    WHERE active;

CREATE INDEX idx_lore_relation_realm_active
    ON lore_relation (realm_id, canon_status, relation_type, source_entity_id, target_entity_id)
    WHERE active;

CREATE TABLE lore_entity_source_evidence (
    realm_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    source_title VARCHAR(200) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    heading VARCHAR(500),
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id, chunk_id),
    CONSTRAINT fk_lore_entity_evidence_entity
        FOREIGN KEY (realm_id, entity_id)
        REFERENCES lore_entity (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_lore_entity_evidence_offsets
        CHECK (start_offset >= 0 AND end_offset > start_offset),
    CONSTRAINT ck_lore_entity_evidence_checksum
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE lore_relation_source_evidence (
    realm_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    source_title VARCHAR(200) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    heading VARCHAR(500),
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (relation_id, chunk_id),
    CONSTRAINT fk_lore_relation_evidence_relation
        FOREIGN KEY (realm_id, relation_id)
        REFERENCES lore_relation (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_lore_relation_evidence_offsets
        CHECK (start_offset >= 0 AND end_offset > start_offset),
    CONSTRAINT ck_lore_relation_evidence_checksum
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE lore_entity_promotion (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    promoted_by UUID NOT NULL REFERENCES codex_user(id),
    promoted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lore_entity_promotion_entity
        FOREIGN KEY (realm_id, entity_id)
        REFERENCES lore_entity (realm_id, id)
        ON DELETE CASCADE
);

CREATE TABLE lore_relation_promotion (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    promoted_by UUID NOT NULL REFERENCES codex_user(id),
    promoted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lore_relation_promotion_relation
        FOREIGN KEY (realm_id, relation_id)
        REFERENCES lore_relation (realm_id, id)
        ON DELETE CASCADE
);

COMMENT ON TABLE lore_entity_source_evidence IS
    'Immutable source snapshots retained even if the originating source is later retired or deleted.';

COMMENT ON TABLE lore_relation_source_evidence IS
    'Immutable source snapshots retained even if the originating source is later retired or deleted.';
