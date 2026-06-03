-- Weekly lifecycle storage. A weekly_plan is one IC's plan for one ISO week,
-- uniquely keyed by (owner, week_key) (KTD 7). The plan moves through
-- DRAFT -> LOCKED -> RECONCILING -> RECONCILED; lock_type records lock
-- provenance (R4) and no_plan flags an auto-locked empty draft (R5), both kept
-- as their own columns (KTD 2). status_deadline drives the U7 backstop sweep.
-- Columns must match the WeeklyPlan entity exactly because Hibernate runs in
-- validate mode in production. gen_random_uuid() is available via pgcrypto
-- (enabled in V1__baseline.sql). The commitment table is added to this same
-- file by U2.
CREATE TABLE weekly_plan (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner              VARCHAR(255) NOT NULL,
    week_key           VARCHAR(16)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    lock_type          VARCHAR(16),
    no_plan            BOOLEAN      NOT NULL DEFAULT FALSE,
    status_deadline    TIMESTAMPTZ,
    -- Inherited from AbstractAuditingEntity. TIMESTAMPTZ (not plain TIMESTAMP) to
    -- match Hibernate's default mapping for the entity's java.time.Instant fields;
    -- a plain TIMESTAMP would interpret the UTC instant against the session zone
    -- and shift it. Populated by Spring Data auditing on JPA writes.
    created_date       TIMESTAMPTZ,
    last_modified_date TIMESTAMPTZ,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255),
    -- One plan per IC per week (KTD 7); mirrors the entity's @UniqueConstraint.
    CONSTRAINT uq_weekly_plan_owner_week_key UNIQUE (owner, week_key)
);

-- A commitment is one item in a weekly_plan. It links to strategy through a
-- single rcdo_node_id FK (KTD 3) — a plain UUID column with a DB foreign key to
-- rcdo_node, mirroring rcdo_node.parent_id, validated for existence in the
-- service before save (R6). planned distinguishes DRAFT-created (immutable once
-- locked, R7) from unplanned post-lock additions (R8). reconciliation_status is
-- null until reconciliation, then one of the ReconciliationStatus values incl.
-- the system-only UNRECONCILED (KTD 5, R19). carried_from_id (self-FK) +
-- carry_week_count record carry-forward lineage (R12–R15). Columns must match
-- the Commitment entity exactly because Hibernate runs in validate mode.
CREATE TABLE commitment (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    weekly_plan_id        UUID         NOT NULL REFERENCES weekly_plan (id),
    rcdo_node_id          UUID         NOT NULL REFERENCES rcdo_node (id),
    title                 VARCHAR(255) NOT NULL,
    planned               BOOLEAN      NOT NULL,
    reconciliation_status VARCHAR(16),
    reconciliation_note   TEXT,
    carried_from_id       UUID         REFERENCES commitment (id),
    carry_week_count      INTEGER      NOT NULL DEFAULT 0,
    -- Inherited from AbstractAuditingEntity. TIMESTAMPTZ (not plain TIMESTAMP) to
    -- match Hibernate's default mapping for the entity's java.time.Instant fields;
    -- a plain TIMESTAMP would interpret the UTC instant against the session zone
    -- and shift it. Populated by Spring Data auditing on JPA writes.
    created_date          TIMESTAMPTZ,
    last_modified_date    TIMESTAMPTZ,
    created_by            VARCHAR(255),
    last_modified_by      VARCHAR(255)
);

-- Commitments are fetched by their owning plan (the weekly lookup) and joined to
-- their strategy node; index both FK columns.
CREATE INDEX idx_commitment_weekly_plan_id ON commitment (weekly_plan_id);
CREATE INDEX idx_commitment_rcdo_node_id ON commitment (rcdo_node_id);
