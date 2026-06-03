-- Seed a representative RCDO tree so workstream C (the weekly lifecycle) has real
-- strategy nodes to link commitments against. Flyway is the single source of
-- schema truth, so seed data lives in a versioned migration, not application
-- bootstrap. Fixed UUIDs keep the seed deterministic (tests and downstream FKs
-- can reference known nodes). Audit columns are set explicitly here because
-- Spring Data auditing listeners do not fire on raw SQL inserts, and the columns
-- carry data even though AbstractAuditingEntity leaves them nullable.
--
-- Shape: 1 Rally Cry -> 2 Defining Objectives -> 1 Outcome each -> Supporting
-- Outcomes, covering all four levels.

-- Rally Cry (root)
INSERT INTO rcdo_node (id, node_type, title, description, parent_id, sort_order, created_date, last_modified_date, created_by, last_modified_by) VALUES
  ('11111111-1111-1111-1111-111111111111', 'RALLY_CRY', 'Become the category leader', 'Top-level strategic rally cry for the year.', NULL, 0, now(), now(), 'system', 'system');

-- Defining Objectives (children of the Rally Cry)
INSERT INTO rcdo_node (id, node_type, title, description, parent_id, sort_order, created_date, last_modified_date, created_by, last_modified_by) VALUES
  ('22222222-2222-2222-2222-222222222221', 'DEFINING_OBJECTIVE', 'Grow recurring revenue', 'Expand ARR through retention and new logos.', '11111111-1111-1111-1111-111111111111', 0, now(), now(), 'system', 'system'),
  ('22222222-2222-2222-2222-222222222222', 'DEFINING_OBJECTIVE', 'Delight existing customers', 'Raise satisfaction and reduce churn.', '11111111-1111-1111-1111-111111111111', 1, now(), now(), 'system', 'system');

-- Outcomes (one per Defining Objective)
INSERT INTO rcdo_node (id, node_type, title, description, parent_id, sort_order, created_date, last_modified_date, created_by, last_modified_by) VALUES
  ('33333333-3333-3333-3333-333333333331', 'OUTCOME', 'Increase ARR by 20%', 'Measurable revenue outcome.', '22222222-2222-2222-2222-222222222221', 0, now(), now(), 'system', 'system'),
  ('33333333-3333-3333-3333-333333333332', 'OUTCOME', 'Lift NPS to 50', 'Measurable satisfaction outcome.', '22222222-2222-2222-2222-222222222222', 0, now(), now(), 'system', 'system');

-- Supporting Outcomes (the common link target for weekly commitments)
INSERT INTO rcdo_node (id, node_type, title, description, parent_id, sort_order, created_date, last_modified_date, created_by, last_modified_by) VALUES
  ('44444444-4444-4444-4444-444444444441', 'SUPPORTING_OUTCOME', 'Ship usage-based billing', 'Enable expansion revenue.', '33333333-3333-3333-3333-333333333331', 0, now(), now(), 'system', 'system'),
  ('44444444-4444-4444-4444-444444444442', 'SUPPORTING_OUTCOME', 'Reduce onboarding time to 1 day', 'Faster time-to-value.', '33333333-3333-3333-3333-333333333332', 0, now(), now(), 'system', 'system');
