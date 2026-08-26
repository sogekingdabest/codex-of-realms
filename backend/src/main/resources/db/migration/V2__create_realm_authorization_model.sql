CREATE TABLE codex_user (
    id UUID PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_codex_user_identity UNIQUE (issuer, subject),
    CONSTRAINT ck_codex_user_issuer_not_blank CHECK (length(btrim(issuer)) > 0),
    CONSTRAINT ck_codex_user_subject_not_blank CHECK (length(btrim(subject)) > 0),
    CONSTRAINT ck_codex_user_display_name_not_blank CHECK (length(btrim(display_name)) > 0)
);

CREATE TABLE realm (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES codex_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_realm_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE realm_membership (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES codex_user(id),
    role VARCHAR(16) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_realm_membership_user UNIQUE (realm_id, user_id),
    CONSTRAINT uk_realm_membership_scope UNIQUE (realm_id, id),
    CONSTRAINT ck_realm_membership_role CHECK (role IN ('OWNER', 'EDITOR', 'PLAYER'))
);

CREATE INDEX idx_realm_membership_user_active
    ON realm_membership (user_id, realm_id)
    WHERE active;

CREATE TABLE access_policy (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    classification VARCHAR(16) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_access_policy_scope UNIQUE (realm_id, id),
    CONSTRAINT ck_access_policy_classification
        CHECK (classification IN ('PUBLIC', 'GM_ONLY', 'SPOILER'))
);

CREATE INDEX idx_access_policy_realm_active
    ON access_policy (realm_id, id)
    WHERE active;

CREATE TABLE access_grant (
    realm_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_id, membership_id),
    CONSTRAINT fk_access_grant_policy
        FOREIGN KEY (realm_id, policy_id)
        REFERENCES access_policy (realm_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_grant_membership
        FOREIGN KEY (realm_id, membership_id)
        REFERENCES realm_membership (realm_id, id)
        ON DELETE CASCADE
);
