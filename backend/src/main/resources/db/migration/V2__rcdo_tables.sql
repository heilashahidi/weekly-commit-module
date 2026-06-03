-- RCDO strategy hierarchy: one self-referential table holding all four levels
-- (Rally Cry -> Defining Objective -> Outcome -> Supporting Outcome). A single
-- table lets a later weekly commitment link to strategy via one rcdo_node_id FK
-- regardless of level. Columns must match the RcdoNode entity exactly because
-- Hibernate runs in validate mode. gen_random_uuid() is available via pgcrypto
-- (enabled in V1__baseline.sql).
CREATE TABLE rcdo_node (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    node_type          VARCHAR(32)  NOT NULL,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    parent_id          UUID         REFERENCES rcdo_node (id),
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    -- Inherited from AbstractAuditingEntity. Populated by Spring Data auditing on
    -- JPA writes; the seed migration (V3) sets them explicitly since auditing
    -- listeners do not fire on raw SQL inserts.
    created_date       TIMESTAMP,
    last_modified_date TIMESTAMP,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255)
);

-- Tree walks descend by parent_id; index it for the assembly query and for
-- future commitment->node lookups.
CREATE INDEX idx_rcdo_node_parent_id ON rcdo_node (parent_id);
