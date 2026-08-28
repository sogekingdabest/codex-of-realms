ALTER TABLE access_policy
    ADD COLUMN name VARCHAR(120),
    ADD COLUMN description VARCHAR(300);

WITH ranked AS (
    SELECT id,
           classification,
           row_number() OVER (
               PARTITION BY realm_id, classification
               ORDER BY created_at, id
           ) AS position
    FROM access_policy
)
UPDATE access_policy policy
SET name = CASE ranked.classification
    WHEN 'PUBLIC' THEN 'Público'
    WHEN 'GM_ONLY' THEN 'Solo dirección'
    ELSE 'Spoiler'
END || CASE
    WHEN ranked.position = 1 THEN ''
    ELSE ' ' || ranked.position
END
FROM ranked
WHERE ranked.id = policy.id;

ALTER TABLE access_policy
    ALTER COLUMN name SET NOT NULL,
    ADD CONSTRAINT ck_access_policy_name_not_blank
        CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    ADD CONSTRAINT ck_access_policy_description_length
        CHECK (description IS NULL OR length(description) <= 300);

CREATE UNIQUE INDEX uk_access_policy_realm_active_name
    ON access_policy (realm_id, lower(name))
    WHERE active;

CREATE TABLE realm_invitation (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL REFERENCES realm(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(16) NOT NULL,
    invited_by UUID NOT NULL REFERENCES codex_user(id),
    accepted_by UUID REFERENCES codex_user(id),
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_realm_invitation_scope UNIQUE (realm_id, id),
    CONSTRAINT ck_realm_invitation_email_not_blank
        CHECK (length(btrim(email)) BETWEEN 3 AND 320),
    CONSTRAINT ck_realm_invitation_role
        CHECK (role IN ('EDITOR', 'PLAYER')),
    CONSTRAINT ck_realm_invitation_resolution
        CHECK (NOT (accepted_at IS NOT NULL AND revoked_at IS NOT NULL)),
    CONSTRAINT ck_realm_invitation_acceptor
        CHECK ((accepted_at IS NULL) = (accepted_by IS NULL))
);

CREATE UNIQUE INDEX uk_realm_invitation_pending_email
    ON realm_invitation (realm_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_realm_invitation_pending_email
    ON realm_invitation (lower(email), realm_id)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
