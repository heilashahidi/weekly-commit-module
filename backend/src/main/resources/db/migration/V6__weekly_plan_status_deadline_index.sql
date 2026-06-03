-- Composite index backing DeadlineBackstopJob's finder
-- (WeeklyPlanRepository.findByStatusAndStatusDeadlineBefore), which runs three
-- times per sweep (once per forward edge: DRAFT, LOCKED, RECONCILING) on a default
-- 60s schedule, org-wide across every principal's plans. As the plan table grows,
-- a full scan per sweep gets expensive; (status, status_deadline) lets the query
-- seek directly to overdue plans of a given status.
CREATE INDEX idx_weekly_plan_status_deadline ON weekly_plan (status, status_deadline);
