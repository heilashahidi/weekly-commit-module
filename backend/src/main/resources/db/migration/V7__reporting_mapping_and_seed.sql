-- Manager -> direct-report mapping (workstream F). Keyed by principal (JWT sub)
-- strings, matching weekly_plan.owner, so manager -> report -> plan-owner joins
-- line up. No User entity: this seeded mapping is the only org model (see the F
-- plan's Key Technical Decisions). A principal is a manager iff it appears as a
-- manager_sub here.
--
-- Columns must match the ReportingEdge entity exactly because Hibernate runs in
-- validate. gen_random_uuid() is available via pgcrypto (enabled in V1). Audit
-- columns are TIMESTAMPTZ to match the entity's Instant fields and are set
-- explicitly here because Spring Data auditing listeners do not fire on raw SQL
-- inserts.
CREATE TABLE reporting_edge (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    manager_sub         VARCHAR(255) NOT NULL,
    report_sub          VARCHAR(255) NOT NULL,
    report_display_name VARCHAR(255) NOT NULL,
    created_date        TIMESTAMPTZ,
    last_modified_date  TIMESTAMPTZ,
    created_by          VARCHAR(255),
    last_modified_by    VARCHAR(255),
    CONSTRAINT uq_reporting_edge_manager_report UNIQUE (manager_sub, report_sub)
);

-- Roll-up queries scope by manager; index the lookup.
CREATE INDEX idx_reporting_edge_manager_sub ON reporting_edge (manager_sub);

-- Seed the manager -> report edges only (the stable mapping). The report subs are
-- synthetic Auth0-shaped subjects reused by the U7 dev demo seeder and the test
-- fixtures so the manager -> report -> plan-owner join lines up. Demo plan and
-- commitment data is created at runtime by the dev-profile seeder (U7), not here:
-- weekly plans are runtime-created and keyed by the current ISO week, which a
-- static migration cannot know. Fixed UUIDs keep the seed deterministic.
INSERT INTO reporting_edge (id, manager_sub, report_sub, report_display_name, created_date, last_modified_date, created_by, last_modified_by) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', 'auth0|manager-mary', 'auth0|report-ava',  'Ava Stone', now(), now(), 'system', 'system'),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'auth0|manager-mary', 'auth0|report-ben',  'Ben Lee',   now(), now(), 'system', 'system'),
  ('aaaaaaaa-0000-0000-0000-000000000003', 'auth0|manager-mary', 'auth0|report-cleo', 'Cleo Park', now(), now(), 'system', 'system'),
  ('aaaaaaaa-0000-0000-0000-000000000004', 'auth0|manager-mary', 'auth0|report-dan',  'Dan Ruiz',  now(), now(), 'system', 'system');
