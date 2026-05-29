-- Baseline migration. No domain tables yet — those arrive with the feature
-- workstreams (RCDO, lifecycle). Enabling pgcrypto makes this a real, applied
-- migration (not a vacuous empty baseline) and provides gen_random_uuid() for
-- later migrations.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
