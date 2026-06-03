-- Non-blocking manager-review annotation (U9, R16/R17). A manager_review is a pure
-- overlay on a weekly_plan: it lives in its OWN table referencing weekly_plan_id, so
-- it never touches the immutable plan or its commitments (resolves origin Outstanding
-- Question (c)). The lifecycle state machine never consults it — a review may be
-- present or absent at any state and a transition never depends on it (R17).
--
-- One review per plan: a UNIQUE constraint on weekly_plan_id; the service upserts
-- (create if absent, else update the comment). reviewer is the reviewing principal,
-- stored explicitly so it is queryable without relying on audit semantics; comment is
-- the review text (nullable). The review timestamp (R16) is the audit
-- created_date/last_modified_date. Columns must match the ManagerReview entity exactly
-- because Hibernate runs in validate mode. gen_random_uuid() is available via pgcrypto
-- (enabled in V1__baseline.sql).
CREATE TABLE manager_review (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    weekly_plan_id     UUID         NOT NULL REFERENCES weekly_plan (id),
    reviewer           VARCHAR(255),
    comment            TEXT,
    -- Inherited from AbstractAuditingEntity. TIMESTAMPTZ (not plain TIMESTAMP) to
    -- match Hibernate's default mapping for the entity's java.time.Instant fields;
    -- a plain TIMESTAMP would interpret the UTC instant against the session zone
    -- and shift it. Populated by Spring Data auditing on JPA writes.
    created_date       TIMESTAMPTZ,
    last_modified_date TIMESTAMPTZ,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255),
    -- One review per plan; mirrors the entity's @UniqueConstraint.
    CONSTRAINT uq_manager_review_weekly_plan_id UNIQUE (weekly_plan_id)
);

-- Reviews are fetched by their reviewed plan (the read + upsert lookup); index the FK.
CREATE INDEX idx_manager_review_weekly_plan_id ON manager_review (weekly_plan_id);
