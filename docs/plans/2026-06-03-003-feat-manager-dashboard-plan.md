---
title: "feat: Manager Dashboard + Team Roll-up (Workstream F)"
type: feat
status: active
date: 2026-06-03
deepened: 2026-06-03
origin: docs/brainstorms/manager-dashboard-requirements.md
---

# feat: Manager Dashboard + Team Roll-up (Workstream F)

## Summary

Build the manager-facing surface for Weekly Commit: a seeded manager→reports org model, manager-scoped authorization, a paginated team roll-up API, and a manager-dashboard micro-frontend whose home is a current-week status board (status + RCDO outcome spread + review state per report) with drill-in to a read-only plan view and review writing. The implementation extends the existing single-sourced `OwnedPlanLoader` access policy once (owner → owner-OR-manager-of-owner) and closes workstream C's currently-open review write, reusing D's RTK Query types and presentational components throughout.

---

## Problem Frame

People managers are the primary persona (`STRATEGY.md`), but no manager-facing surface exists and the system has no model of who manages whom — principals are opaque JWT subjects. Workstream C also shipped the `ManagerReview` write deliberately unguarded (any authenticated principal may review any plan) because the org model it needed did not exist yet. This plan supplies that model and the dashboard over it. See origin: `docs/brainstorms/manager-dashboard-requirements.md`.

---

## Requirements

**Org model & authorization**
- R1. Seeded manager→direct-reports mapping (Flyway migration, not self-service), keyed by principal identity; a principal is a manager iff they have ≥1 report.
- R2. Mapping carries a human-readable display name per report so the board never shows opaque principal ids.
- R3. A manager may read the plans, commitments, and reviews of their direct reports only; non-reports denied. Extends D's owner-only plan read to owner-OR-manager-of-owner.
- R4. Creating/updating a manager review is restricted to the reviewed plan owner's manager; C's open write path is closed.
- R5. Reviews remain non-blocking — writing/omitting a review never affects the lifecycle state machine (preserves C's R17).

**Team status board**
- R6. Dashboard home is a current-week status board, one row per direct report.
- R7. Row shows current-week plan status (DRAFT/LOCKED/RECONCILING/RECONCILED), or a distinct "no plan yet" state.
- R8. Row shows the RCDO outcome spread for that report's current-week commitments (which Outcomes the week points at, with counts).
- R9. Row shows whether a manager review exists (review-done indicator).
- R10. Row links to the report's plan detail.
- R11. Reports with no current-week plan are included; absence is a signal.

**Plan detail & review**
- R12. From a row, a read-only view of the report's plan: commitments, linked Supporting Outcomes, planned-vs-actual when reconciled. No edit affordances.
- R13. Manager can create/update the single non-blocking review from the detail view, and see reviewer + timestamp.
- R14. Review writing available whenever the plan exists (no lifecycle-status gate).

**Platform & performance**
- R15. Ships as its own micro-frontend remote module (Module Federation), lazy-loaded.
- R16. Team roll-up query supports pagination (Spring Data Pageable) sized for up to 2000 records.
- R17. Plan retrieval stays within the <200ms latency budget; reuse C's read paths, no N+1.
- R18. Reuse D's RTK Query types and status styling (`statusStyles`/`StatusBadge`) rather than re-deriving.

**Origin actors:** A1 People manager (primary), A2 Individual Contributor / report, A3 Lifecycle engine (workstream C).
**Origin flows:** F1 Scan the current-week team board, F2 Drill into a report's plan and review it.
**Origin acceptance examples:** AE1 (covers R3), AE2 (covers R4), AE3 (covers R7, R11), AE4 (covers R8).

---

## Scope Boundaries

- No first-class User entity — F uses principal identity plus a seeded reporting mapping (entity-backed for querying, seed-only for data).
- No self-service org assignment or org-admin UI; the mapping is seeded.
- No editing of a report's plan or commitments; F is read-only over IC data plus the review write.
- No chess-layer categorization/prioritization (workstream E).
- No Outlook Graph / notifications for review nudges (out of assessment scope).

### Deferred to Follow-Up Work

- Historical metrics/trends dashboard (alignment fidelity over time, completion rate, review-turnaround charts): future iteration.
- Transitive / skip-level org roll-up: future iteration; v1 is direct reports only.

---

## Context & Research

### Relevant Code and Patterns

- **Migrations + seed:** `backend/src/main/resources/db/migration/` — next version is **V7**. Mirror `V2__rcdo_tables.sql` (DDL) + `V3__rcdo_seed.sql` (seed). Audit columns (`created_date/last_modified_date/created_by/last_modified_by`) must be populated explicitly with `'system'` — auditing listeners do not fire on raw SQL inserts. `TIMESTAMPTZ`, `VARCHAR(255)` principal columns matching `weekly_plan.owner`, `pgcrypto`/`gen_random_uuid()` already enabled.
- **Access policy:** `backend/src/main/java/com/weeklycommit/lifecycle/OwnedPlanLoader.java` — single-sourced owner check (404 missing / 403 wrong owner via `ResponseStatusException`), injected into `LifecycleService`, `CommitmentService`, `ReconciliationService`, `MetricsService`, `CarryForwardService`. The clean extension point for manager-of-owner.
- **Review backend:** `lifecycle/ManagerReviewService.java` (`upsertReview`/`getReview`, currently existence-only auth), `ManagerReviewController.java` (`PUT/GET /api/lifecycle/plans/{planId}/review`, 204 on absent), `ManagerReviewDto.java` (record, already mirrored in the frontend).
- **Principal + security:** `config/JwtPrincipalResolver.java` (`currentPrincipal()` = JWT subject), `config/SecurityConfig.java` (stateless OAuth2, imperative service-layer authz, no `@PreAuthorize`). `LifecycleService.getOrCreateCurrentPlan` sets `owner = currentPrincipal()` — **confirms the manager→report→owner join keys on the same string** (resolves the origin's identity-alignment assumption).
- **Current week + RCDO ancestry:** `WeekKey.current(Clock)` → ISO week key; `WeeklyPlanRepository.findByOwnerAndWeekKey`. `RcdoNode` is an adjacency list (`parentId` walks SUPPORTING_OUTCOME→OUTCOME→DEFINING_OBJECTIVE→RALLY_CRY); `RcdoService` loads the whole tree once with `findAll()` and assembles in memory (cycle-guarded) — reuse this for the outcome-spread walk rather than recursive SQL.
- **Read-API conventions:** thin `@RestController`, `record` DTOs with `from(entity)` factories, `@Service` + `@Transactional(readOnly=true)`. Templates: `lifecycle/LifecycleController.java`, `rcdo/RcdoController.java`, `rcdo/RcdoService.java`.
- **Frontend federation:** `frontend/vite.config.ts` — `federation({name:'weeklyCommit', exposes:{'./WeeklyCommitApp':...}})`. Add a second `exposes` entry. Mirror `frontend/src/WeeklyCommitApp.tsx` (Provider + Suspense + lazy inner).
- **RTK Query:** `frontend/src/store/api.ts` — single `createApi` slice (no `injectEndpoints`), `tagTypes` include `WeeklyPlan/Commitment/ManagerReview/PlanMetrics`. Reuse `WeeklyPlanDto/CommitmentDto/PlanMetricsDto/ManagerReviewDto/RcdoNode/PlanStatus`. Reuse the `getManagerReview` text-then-parse 204→null `responseHandler`.
- **D reusable UI:** `frontend/src/lib/statusStyles.ts`, `components/StatusBadge.tsx`, `lib/problemDetail.ts`, `lib/formatTimestamp.ts`, `components/ManagerReviewNote.tsx` (read-only review display), `routes/myweek/SummaryView.tsx` + `components/CommitmentRow.tsx` `mode="frozen"` (read-only commitment renderer).
- **Adaptive shell:** `frontend/src/routes/MyWeek.tsx` — `React.lazy` + status switch, no router.
- **Test harness:** backend `support/AbstractPostgresIT.java` (Zonky embedded Postgres, no Docker), `TestSecurityConfig` (`Bearer valid-token`, subject `auth0|test-user`), `lifecycle/LifecycleControllerTest.java` (403/404/401 cases), `rcdo/RcdoMigrationTest.java` (applied-count + seed-shape). Frontend `src/test/renderWithStore.tsx` + `vi.stubGlobal('fetch', ...)` (template `__tests__/LockedView.test.tsx`), happy-dom.

### Institutional Learnings

- No `docs/solutions/` store exists; durable learnings live inline in `docs/plans/*` (KTD / Risks). Capture F's learnings the same way after it lands — especially the second MF remote, the first unproven pattern in this repo.
- **JDK gotcha (carried through B/C/D):** host JDK 25 breaks Gradle 8.10.2 — backend checks must run `cd backend && JAVA_HOME=<JDK21> ./gradlew check`. The 80% JaCoCo line gate + Spotless + SpotBugs + `checkNoLombokData` all run under `check`; new F classes are not excluded.
- **MF verify gotcha (from D):** verify the remote with `npm run build && npm run preview`, not the dev server — the dev server doesn't exercise the federated `remoteEntry` load path.

### External References

- None — the stack is well-established locally (A/B/C/D are direct examples). No external research run.

---

## Key Technical Decisions

- **Reporting mapping is entity-backed, seeded:** a JPA entity + repository mirroring `RcdoNode`, populated only by the V7 seed migration. Entity-backed because the roll-up query must join/query it; no User entity (origin Key Decision).
- **Authorization extends `OwnedPlanLoader`, not a fork:** add a `loadOwnedOrManaged(planId)` method (owner OR `reportingRepository.existsByManagerSubAndReportSub(currentPrincipal, owner)`, else 403). The manager-scoped **read** call sites switch to it; **write/transition** call sites stay on `loadOwned` (owner-only). This single-sources the auth matrix below.
- **Exact call-site switch (enumerated, not "the read paths"):** `LifecycleService.getPlan` and `LifecycleService.listCommitments` → `loadOwnedOrManaged`. `MetricsService.getMetrics` → `loadOwnedOrManaged` (R12 needs planned-vs-actual; otherwise the manager 403s on metrics). Everything else stays `loadOwned`: all `CommitmentService` create/edit/delete, all `ReconciliationService` transitions, and `CarryForwardService` — these are IC-only writes and must 403 for a manager. U2 must assert (test) that a manager gets 403 on every write/transition against a report's plan.
- **Auth matrix:** plan/commitments read = owner OR manager-of-owner; metrics read = owner OR manager-of-owner; review read = owner OR manager-of-owner (the IC still sees their own review, per D — note the current `getReview` has **zero** ownership check, so this is a check added from scratch, not tightened); review write = manager-of-owner only (owner self-review → 403); all plan/commitment/reconciliation writes = owner only.
- **Outcome spread grouped at RCDO Outcome level:** group each commitment's linked node by its OUTCOME ancestor. The V3 seed has one Rally Cry but **two Outcomes** (the ARR and NPS outcomes), so Outcome-level grouping differentiates — provided the demo plan data (U7) spreads commitments across both. Resolve via a new `RcdoService` ancestor helper (build a flat `id→parentId` map from the existing `findAll()`, walk up, same cycle guard). **Above-Outcome fallback:** `Commitment.rcdoNodeId` may legally link at any level; a commitment linked at or above OUTCOME (RALLY_CRY / DEFINING_OBJECTIVE) has no OUTCOME ancestor and buckets under an explicit `"Other"` chip rather than being dropped.
- **Roll-up avoids N+1:** compute `WeekKey.current(clock)` once, batch-load reports' plans for that week (`findByOwnerInAndWeekKey`), batch-load their commitments, load the RCDO tree once. Bounded query count, within the <200ms budget.
- **Pagination returns a small `PageDto<T>` record** (`content` + page metadata), not raw `PageImpl` — Spring Boot 3.3 warns on serializing `PageImpl` directly. F sets the repo's first pagination precedent.
- **Second exposed module, not a separate remote build:** add `'./ManagerDashboardApp'` to the existing `exposes` map (R15 read as "its own remote module"). Infra supports one `federation({name})`; a literally separate remote is heavier and unproven.
- **Board↔detail navigation via local state, no router:** F introduces a report-selection axis as component state with a `React.lazy` detail screen behind `Suspense`, consistent with D's no-router house style. Because there is no router to manage focus, the detail mount moves focus to its heading and back-navigation restores focus to the originating row (a consequence of the no-router choice, not optional polish).
- **`NO_PLAN` is not a `PlanStatus` value:** the frontend `PlanStatus` union and `STATUS_STYLES` map are exhaustive; feeding a `NO_PLAN` sentinel into them is a tsc-strict error / runtime crash. Model `TeamRowDto.status` as `PlanStatus | null` (null = no current-week plan) and render a dedicated "no plan yet" badge for the null case, bypassing `statusStyle()`. Do not widen the shared `PlanStatus` type.
- **Demo data via a dev-profile runtime seeder, not Flyway:** there are no seeded weekly plans in the repo (plans are created at runtime by `LifecycleService.getOrCreateCurrentPlan`), so a static Flyway seed cannot populate the board — and `WeekKey.current(Clock)` advances weekly, so a fixed `week_key` in SQL would go stale. The reporting *edges* (the manager→report mapping) are seeded via Flyway V7 (stable). The demo *plan + commitment* data for reports is created by a dev-profile-only `CommandLineRunner` (U7) that computes `WeekKey.current` at startup and inserts plans + RCDO-linked commitments (spanning both Outcomes) for the seeded report subs — populating a current, differentiated board every week in dev without polluting prod and without needing multi-principal login.
- **Review comment is bounded:** add a `@Size(max=2000)` constraint (and matching DB column length) on the review comment; the current `ReviewRequest`/entity accept an unbounded string. Return 400 on violation; the frontend validates non-empty + max length before submit.

---

## Open Questions

### Resolved During Planning

- Current-week derivation: `WeekKey.current(Clock)` is pure-calendar, no per-user state. Resolved.
- Principal-identity alignment: `JwtPrincipalResolver.currentPrincipal()` is the same string stamped as `WeeklyPlan.owner`; the seed keys on those subs. Resolved.
- Reporting table entity-backed vs pure-seed: entity-backed (see KTD). Resolved.
- Outcome-spread grouping level: Outcome (see KTD). Resolved.
- Pagination response shape: `PageDto<T>` record (see KTD). Resolved.
- Second remote interpretation: second exposed module (see KTD). Resolved.
- Roll-up query shape: batched, in-memory RCDO walk (see KTD). Resolved.
- Non-manager / zero-reports hitting the roll-up: **403**, not a silent empty 200 (see auth matrix). Resolved.
- `NO_PLAN` representation: `status: PlanStatus | null` + dedicated frontend badge; the `PlanStatus` union is not widened (see KTD). Resolved.
- Commitments linked at/above OUTCOME level: bucket under an `"Other"` chip (see KTD). Resolved.
- Demo board population: dev-profile `CommandLineRunner` (U7), not a Flyway plan seed — avoids the static-week-key trap and multi-principal login. Resolved.
- Multi-principal test authentication: extend `TestSecurityConfig` to a token→subject map (U2). Resolved.
- `MetricsService` access for managers: switch `getMetrics` to `loadOwnedOrManaged` (R12). Resolved.
- Review comment bound: `@Size(max=2000)` backend + frontend non-empty/counter (see KTD). Resolved.

### Deferred to Implementation

- Exact `PageDto` field set and whether the frontend needs `totalPages` vs `totalElements` for v1 (small teams may never paginate in the UI).
- Whether the read-only detail reuses `SummaryView`'s metrics block by extraction or renders a thin manager-specific metrics summary — decide when wiring the component against a real reconciled plan.
- Seed roster contents (which `auth0|...` subs + display names) to make the demo legible against existing seeded plans.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

Team roll-up data flow (one manager request → one board):

```
GET /api/manager/team?page=&size=   (principal = manager)
  └─ if principal has zero reports → 403 (not an empty 200; see auth matrix)
     reportingRepository.findByManagerSub(manager, pageable)  → [report subs + display names]
       weekKey = WeekKey.current(clock)
       weeklyPlanRepository.findByOwnerInAndWeekKey(reportSubs, weekKey)  → plans (some reports absent = "no plan")
       commitmentRepository.findByWeeklyPlanIdIn(planIds)                 → commitments
       rcdoService.outcomeAncestorOf(nodeId)  (flat id→parentId map from findAll) → OUTCOME, or "Other" if above-Outcome
       managerReviewRepository.findWeeklyPlanIdsByWeeklyPlanIdIn(planIds) → review-exists flags (projection, not full rows)
  → PageDto<TeamRowDto>{ report, displayName, status: PlanStatus|null, outcomeSpread[], reviewExists }
```

Authorization (single-sourced in `OwnedPlanLoader` + review service):

```
plan/commitments read   : owner OR manager-of-owner   (loadOwnedOrManaged)
metrics read            : owner OR manager-of-owner   (loadOwnedOrManaged)
review read             : owner OR manager-of-owner   (new check — currently zero authz)
review write (upsert)   : manager-of-owner ONLY        (owner self-review → 403)
plan/commit/recon writes: owner ONLY                   (loadOwned — manager → 403)
roll-up (zero reports)  : 403 (not empty 200)
non-report / non-manager: 403   |   unknown plan: 404   |   no token: 401
```

---

## Implementation Units

### U1. Reporting model: entity, migration, seed

**Goal:** A seeded, queryable manager→direct-reports mapping keyed by principal, with display names.

**Requirements:** R1, R2

**Dependencies:** None

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__reporting_mapping_and_seed.sql`
- Create: `backend/src/main/java/com/weeklycommit/manager/ReportingEdge.java` (entity, extends `AbstractAuditingEntity`)
- Create: `backend/src/main/java/com/weeklycommit/manager/ReportingRepository.java`
- Test: `backend/src/test/java/com/weeklycommit/manager/ReportingMigrationTest.java`

**Approach:**
- One table (e.g. `reporting_edge`): `id uuid`, `manager_sub varchar(255)`, `report_sub varchar(255)`, `report_display_name varchar(255)`, audit columns; unique on `(manager_sub, report_sub)`, index on `manager_sub`.
- Seed the manager→report **edges only** (the stable mapping). Explicit `'system'` audit values, fixed UUIDs, `TIMESTAMPTZ`. The report subs are synthetic Auth0-shaped subjects (e.g. `auth0|report-ava`) chosen here and reused by the U7 demo seeder and the test fixtures so the manager→report→plan-owner join lines up. Do **not** assume pre-existing seeded plan owners — none exist; demo plan data is U7.
- Repository methods: `existsByManagerSubAndReportSub(String, String)`, `findByManagerSub(String, Pageable)`, `existsByManagerSub(String)` (for the roll-up's zero-reports → 403 check).

**Patterns to follow:** `db/migration/V2__rcdo_tables.sql` + `V3__rcdo_seed.sql`; `rcdo/RcdoNode.java`; `rcdo/RcdoNodeRepository.java`; test `rcdo/RcdoMigrationTest.java`.

**Test scenarios:**
- Happy path: migration applies exactly once (`flyway.info().applied()` count) and context loads under `ddl-auto: validate` (entity matches DDL).
- Happy path: seeded edges exist — a known manager resolves ≥1 report with a non-null display name.
- Edge case: `existsByManagerSubAndReportSub` returns false for a non-edge pair.

**Verification:** `JAVA_HOME=<JDK21> ./gradlew check` green; the new table validates and the seed roster is queryable.

---

### U2. Manager-scoped authorization

**Goal:** Extend the access policy to owner-OR-manager-of-owner for reads, and close C's open review write to manager-of-owner only.

**Requirements:** R3, R4, R5

**Dependencies:** U1

**Files:**
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/OwnedPlanLoader.java` (add `loadOwnedOrManaged`; inject `ReportingRepository`)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/LifecycleService.java` (`getPlan` + `listCommitments` use `loadOwnedOrManaged`; writes unchanged)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/MetricsService.java` (`getMetrics` uses `loadOwnedOrManaged` — R12 needs planned-vs-actual)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/ManagerReviewService.java` (write = manager-of-owner only; read = owner-or-manager-of-owner; load the plan, not `existsById`)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/ManagerReviewController.java` (add `@Size`/`@Valid` on the review comment) and the `ManagerReview` entity / V5-or-later column length
- Modify (test infra): `backend/src/test/java/com/weeklycommit/support/TestSecurityConfig.java` (decode multiple tokens → distinct subjects)
- Test: `ManagerReviewControllerTest.java` (re-own plans + flip reviewer assertions + self-review 403), `LifecycleControllerTest.java` (manager-scope read cases), `MetricsControllerTest` (manager metrics read)

**Approach:**
- `loadOwnedOrManaged(planId)`: load plan (404 if missing) → pass if `owner == principal` OR `reportingRepository.existsByManagerSubAndReportSub(principal, owner)` → else 403. Keep `loadOwned` for owner-only (write/transition) callers per the KTD call-site enumeration.
- **`ManagerReviewService` must load the `WeeklyPlan`** (it currently only does `existsById`) to obtain the owner string. Write: require principal is the owner's manager (403 otherwise, including owner self-review). Read: the current `getReview` has **no** ownership check at all — add owner-OR-manager-of-owner from scratch (any authenticated principal can read any review today). Never mutates plan/commitments (preserves R5).
- **Multi-principal test harness (blocking dependency for AE1/AE2):** `TestSecurityConfig` currently decodes the single `VALID_TOKEN` → one hardcoded subject, so no test can act as two principals. Extend it to a token→subject map (e.g. a manager token and report tokens) plus a helper to act as an arbitrary principal. Review happy-path plans are owned by a report sub while the acting principal is the manager; flip the existing `reviewer == OWNER` assertions to `reviewer == manager`.

**Execution note:** Add the failing manager-scope authz tests first (after the harness extension), then wire the checks.

**Patterns to follow:** existing `OwnedPlanLoader.loadOwned` 403/404 shape; `LifecycleControllerTest` 403/404/401 cases; `support/TestSecurityConfig.java` decoder.

**Test scenarios:**
- Covers AE1. Integration: manager M reads report I's plan → 200; M reads non-report J's plan → 403; unknown plan → 404; no token → 401.
- Covers AE2. Integration: M (I's manager) upserts a review on I's plan → 200, reviewer stamped as M; a principal who is not I's manager upserts → 403; owner I self-reviews → 403.
- Happy path: review read by owner I → 200 (IC still sees their own review); by M → 200; by an unrelated principal (neither owner nor manager) → 403.
- Happy path: M reads metrics for I's reconciled plan → 200; unrelated principal → 403.
- Error path: a write/transition (lock, add commitment, start reconciling, carry-forward) by manager M against report I's plan → 403 (writes stay owner-only).
- Edge case: review comment exceeding the max length → 400; empty/whitespace comment handling per the decided rule.
- Edge case: lifecycle transition on a plan with/without a review is unaffected (R5 — review never gates state).

**Verification:** `JAVA_HOME=<JDK21> ./gradlew check` green incl. 80% coverage; the open-write path is closed, review read is now authz'd, manager reads (plan/commitments/metrics/review) work, and all writes remain owner-only.

---

### U3. Team roll-up service + paginated API

**Goal:** One endpoint returning a manager's current-week board rows, paginated.

**Requirements:** R6, R7, R8, R9, R11, R16, R17

**Dependencies:** U1, U2

**Files:**
- Create: `backend/src/main/java/com/weeklycommit/manager/ManagerDashboardController.java`
- Create: `backend/src/main/java/com/weeklycommit/manager/ManagerDashboardService.java`
- Create: `backend/src/main/java/com/weeklycommit/manager/TeamRowDto.java`, `OutcomeCountDto.java`, `PageDto.java`
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/WeeklyPlanRepository.java` (`findByOwnerInAndWeekKey`)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/CommitmentRepository.java` (`findByWeeklyPlanIdIn`)
- Modify: `backend/src/main/java/com/weeklycommit/lifecycle/ManagerReviewRepository.java` (`findWeeklyPlanIdsByWeeklyPlanIdIn` projection — review-exists flags without loading full rows)
- Modify: `backend/src/main/java/com/weeklycommit/rcdo/RcdoService.java` (add an ancestor helper, e.g. `outcomeAncestorOf(UUID)` / a flat `id→parentId` map; today only `getTree`/`getNode` nested DTOs exist)
- Test: `backend/src/test/java/com/weeklycommit/manager/ManagerDashboardControllerTest.java`

**Approach:**
- `GET /api/manager/team` (Spring auto-binds `Pageable`). Service: **reject with 403 if `reportingRepository.existsByManagerSub(principal)` is false** (a non-manager / zero-reports principal must not get a silent empty 200) → page the manager's reports → compute `WeekKey.current(clock)` → batch-load plans (`findByOwnerInAndWeekKey`), commitments (`findByWeeklyPlanIdIn`), review-exists flags (projection) → resolve each commitment's `rcdoNodeId` to its OUTCOME ancestor via the new `RcdoService` helper, bucketing above-Outcome links under `"Other"` → assemble `TeamRowDto{report, displayName, status: PlanStatus|null, outcomeSpread:[{outcome,count}], reviewExists}`.
- Reports with no plan for the week emit a row with `status = null` (R7/R11) — **not** a `NO_PLAN` enum value (see KTD: NO_PLAN is a frontend rendering concern, not a status value).
- Return `PageDto<TeamRowDto>` (content + page metadata).

**Patterns to follow:** `rcdo/RcdoController.java` + `RcdoService.java` (in-memory tree, `findAll`); `lifecycle/*Dto.java` record style; `LifecycleControllerTest`.

**Test scenarios:**
- Covers AE3. Integration: a report with no current-week plan appears with `status = null` (the "no plan" row).
- Covers AE4. Integration: a report whose plan has commitments under two different Outcomes shows both with counts (use the real V3 Outcomes, not the illustrative "Ship v2"/"Retention" titles).
- Happy path: row carries correct status, review-exists true/false, and display name.
- Edge case: a commitment linked at or above OUTCOME level (RALLY_CRY/DEFINING_OBJECTIVE) buckets under `"Other"`, not dropped or NPE.
- Edge case: a non-manager / zero-reports principal → 403.
- Edge case: pagination — `size` smaller than report count returns the right page slice and totals.
- Integration: no N+1 — the per-board query count does not scale with report count (use Hibernate `Statistics` query-count assertion, or structure so the batch finders + single `findAll` tree load are provably used).

**Verification:** `JAVA_HOME=<JDK21> ./gradlew check` green; board data correct for seeded fixtures; non-manager 403; paginated.

---

### U4. Frontend data layer: team-week query + review-write mutation

**Goal:** RTK Query endpoints for the board (paginated) and the review write, reusing D's types.

**Requirements:** R8, R9, R13, R14, R16, R18

**Dependencies:** U3 (API contract)

**Files:**
- Modify: `frontend/src/store/api.ts` (add `getTeamWeek` query + `upsertManagerReview` mutation; new tag `TeamWeek`; reuse existing DTO types)
- Test: `frontend/src/__tests__/managerApi.test.tsx`

**Approach:**
- `getTeamWeek({page,size})` → `/api/manager/team`, `providesTags:['TeamWeek']`. Add TS types for `TeamRowDto`/`OutcomeCountDto`/`PageDto` mirroring the backend records; reuse `PlanStatus`/`ManagerReviewDto`. `TeamRowDto.status` is `PlanStatus | null` (null = no plan) — do **not** introduce a `NO_PLAN` member into the `PlanStatus` union.
- `upsertManagerReview({planId, comment})` → `PUT /api/lifecycle/plans/{planId}/review`, `invalidatesTags:['ManagerReview','TeamWeek']` so the board's review-done flag and the detail view refresh.
- Single `createApi` slice, no `injectEndpoints`. Reuse the 204→null `responseHandler` where relevant.

**Patterns to follow:** existing endpoints in `store/api.ts`; the `getManagerReview` read; `__tests__/lifecycleApi.test.tsx`.

**Test scenarios:**
- Happy path: `getTeamWeek` issues a bearer-authed request to the team route with `page/size`; parses `PageDto` content.
- Happy path: `upsertManagerReview` issues `PUT .../review` with the comment body.
- Integration: a successful review upsert invalidates `ManagerReview` + `TeamWeek` (refetch fires).
- Error path: a 403 review write surfaces a problem-detail error (consumed by the UI in U6).

**Verification:** `npm run lint && npm test` green; types compile under tsc-strict.

---

### U5. Manager dashboard remote + current-week status board

**Goal:** The second federation remote and the board screen (F1).

**Requirements:** R6, R7, R8, R9, R10, R11, R15, R18

**Dependencies:** U4

**Files:**
- Modify: `frontend/vite.config.ts` (add `'./ManagerDashboardApp'` to `exposes`)
- Create: `frontend/src/ManagerDashboardApp.tsx` (Provider + Suspense + lazy inner)
- Create: `frontend/src/routes/ManagerDashboard.tsx` (board shell + board↔detail local-state switch, lazy detail)
- Create: `frontend/src/routes/manager/TeamBoard.tsx` (the rows)
- Test: `frontend/src/__tests__/TeamBoard.test.tsx`

**Approach:**
- Mirror `WeeklyCommitApp.tsx` for the entry. `ManagerDashboard` holds `selectedPlanId` local state: null → board, set → lazy-loaded detail (U6).
- Board renders one row per `TeamRowDto`: for `status != null`, reuse `StatusBadge`; for `status == null`, render a dedicated dimmed "no plan yet" badge (do **not** pass null/NO_PLAN to `statusStyle()`). Outcome-spread chips with counts, capped at 3 visible with a `"+N more"` non-interactive label on overflow. Review-done indicator: a `✓` (aria-label "Review complete") when `reviewExists`, an em-dash (aria-label "No review") otherwise. Row click sets `selectedPlanId`.
- Distinct branches: `isLoading` → a loading placeholder (follow `MyWeek.tsx`); `isError` → `problemDetailMessage` (a non-manager 403 surfaces here, not a blank board); success with empty content → an explicit "No direct reports found" empty state.
- **Pagination v1:** request page 0 with a fixed `size` (e.g. 50) and render no paging controls; teams are small. Document this as a known v1 limitation (a report beyond 50 would be silently omitted — acceptable for v1, revisit with the deferred trends work).
- Reuse `statusStyles`/`StatusBadge` for non-null statuses (R18).

**Patterns to follow:** `WeeklyCommitApp.tsx`, `routes/MyWeek.tsx` (lazy + Suspense, no router), `components/StatusBadge.tsx`, `lib/statusStyles.ts`.

**Test scenarios:**
- Covers AE3. Component: a row with `status: null` renders the dimmed "no plan yet" badge (not via `statusStyle`).
- Covers AE4. Component: a row renders outcome-spread chips with the right labels + counts; a row with 4+ outcomes shows 3 chips + "+N more".
- Happy path: rows render status, display name, and the review-done indicator (✓ vs —) from stubbed `getTeamWeek`.
- Happy path: clicking a row transitions to the detail view (selected plan state set).
- Edge case: success with empty content renders the "No direct reports found" empty state.
- Error path: a failed/403 roll-up fetch shows a problem-detail message, not a blank board.

**Verification:** `npm run build && npm run preview` emits `remoteEntry.js` and the second exposed module loads; board renders against stubbed data.

---

### U6. Read-only plan detail + review write

**Goal:** The drill-in screen (F2): read-only plan + review writing.

**Requirements:** R12, R13, R14, R5

**Dependencies:** U4, U5

**Files:**
- Create: `frontend/src/routes/manager/ReportPlanDetail.tsx`
- Create: `frontend/src/components/ManagerReviewForm.tsx`
- Test: `frontend/src/__tests__/ReportPlanDetail.test.tsx`, `frontend/src/__tests__/ManagerReviewForm.test.tsx`

- Detail reads the selected report's plan + commitments + metrics via the (now manager-authorized) plan-by-id / metrics read paths, rendering `CommitmentRow mode="frozen"` + `StatusBadge` + a planned-vs-actual block when reconciled. No edit/delete/transition controls.
- Review: reuse `ManagerReviewNote` for display; add `ManagerReviewForm` (comment textarea + submit) calling `upsertManagerReview`. Form behavior: submit disabled while empty/whitespace and while the mutation is in-flight (prevents double-submit); a max-length matching the backend `@Size(2000)` with a visible counter; on success show a transient "Review saved" confirmation (the invalidation also refreshes the `ManagerReviewNote` display + board flag); on 403/failure show `problemDetailMessage` without clearing the textarea.
- Back affordance returns to the board (clears `selectedPlanId`). Focus management (no router): on detail mount move focus to the detail heading; on back, restore focus to the originating row.

**Patterns to follow:** `routes/myweek/SummaryView.tsx`, `components/CommitmentRow.tsx` (`mode="frozen"`), `components/ManagerReviewNote.tsx`, `lib/formatTimestamp.ts`.

**Test scenarios:**
- Happy path: detail renders commitments read-only (no edit/delete controls present) with their linked outcomes.
- Happy path: submitting a review calls the upsert, disables submit while in-flight, and shows the saved reviewer + timestamp + a "Review saved" confirmation.
- Edge case: submit is disabled for an empty/whitespace comment; the counter reflects remaining length.
- Edge case: a plan with no existing review (204) shows an empty review state, not an error.
- Error path: a 403 review write surfaces the problem-detail message and does not wipe the form.
- Edge case: a reconciled plan shows planned-vs-actual (metrics fetched via the manager-authorized path); a DRAFT plan does not.

**Verification:** `npm run build` (tsc-strict + build) green; detail is read-only and review write round-trips.

---

### U7. Dev-profile demo data seeder

**Goal:** Populate a current, differentiated board for the demo without static-week SQL or multi-principal login.

**Requirements:** R6, R7, R8 (demo legibility); supports the demo video deliverable.

**Dependencies:** U1 (report subs), U3 (so the board can read it)

**Files:**
- Create: `backend/src/main/java/com/weeklycommit/manager/DemoDataSeeder.java` (`@Profile("dev")` `CommandLineRunner`)
- Test: `backend/src/test/java/com/weeklycommit/manager/DemoDataSeederTest.java`

**Approach:**
- On startup under the `dev` profile only, compute `WeekKey.current(clock)` and, for each seeded report sub (U1), create a current-week `WeeklyPlan` (varied statuses across reports — DRAFT/LOCKED/RECONCILED, and leave one report with no plan to exercise the null-status row) with RCDO-linked commitments that **span both V3 Outcomes** so the outcome-spread chips differentiate and AE4 is demonstrable. Idempotent: skip seeding a report whose current-week plan already exists.
- Not a Flyway migration (the week advances; static `week_key` would go stale) and not in prod (dev profile only). Reuse the lifecycle/commitment services or repositories to insert so audit fields populate.

**Execution note:** This is demo-support, not a correctness dependency — tests use their own fixtures, not this seeder.

**Patterns to follow:** `config/SecurityConfig` dev-profile gating; `WeekKey.current`; the lifecycle create paths.

**Test scenarios:**
- Happy path: under the dev profile, after startup each non-skipped report sub has a current-week plan with commitments across both Outcomes.
- Edge case: idempotent — running twice does not duplicate plans for a report.
- Edge case: at least one report is intentionally left plan-less so the board's null-status row is demonstrable.

**Verification:** running the backend under the dev profile then opening the dashboard shows a populated, differentiated, current-week board.

---

## System-Wide Impact

- **Interaction graph:** `OwnedPlanLoader` is shared by five services; the `loadOwnedOrManaged` addition is purely additive (existing `loadOwned` callers unchanged). Only the explicit read paths in `LifecycleService` switch methods.
- **Error propagation:** 401 (no token) from security; 403 (non-report / non-manager / owner self-review) and 404 (unknown plan) from the service layer via `ResponseStatusException`; frontend renders all via `problemDetailMessage`.
- **State lifecycle risks:** reviews remain a pure annotation (R5) — no plan/commitment mutation. Tag invalidation (`ManagerReview`+`TeamWeek`) keeps the board flag and detail consistent after a write.
- **API surface parity:** review write moves from open to manager-gated — a breaking change for any caller relying on open writes. Only D (read-only) consumes reviews today, so blast radius is the test suite + the new F UI.
- **Integration coverage:** the manager-scope 403/404/401 matrix and the no-N+1 roll-up are integration-level; cover with MockMvc against embedded Postgres, not mocks.
- **Unchanged invariants:** the lifecycle state machine, IC ownership semantics on D's own paths, and the single-`createApi`-slice / no-router / no-`injectEndpoints` frontend conventions are explicitly unchanged.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| No seeded weekly plans exist (plans are runtime-created) → a static seed leaves the board all-empty | U7 dev-profile `CommandLineRunner` computes the current `WeekKey` and inserts report plans+commitments at startup; reporting *edges* (U1) and report subs are shared with U7 + tests so the join lines up. |
| Identity-join mismatch (seed sub ≠ plan owner sub) | Alignment verified (`currentPrincipal()` == `WeeklyPlan.owner`); U1 fixes the synthetic report subs and U7/tests reuse them; case-sensitive exact match (no normalization — acceptable for synthetic Auth0-shaped subs). |
| Test harness mints one subject → AE1/AE2 multi-principal cases unwritable | U2 extends `TestSecurityConfig` to a token→subject map + an act-as helper before the authz tests; re-own happy-path plans to a report sub while acting as the manager. |
| Loosening `OwnedPlanLoader` over-widens read access across 5 services | KTD enumerates exactly which call sites switch (`getPlan`, `listCommitments`, `getMetrics`) vs stay owner-only; U2 asserts a manager gets 403 on every write/transition. |
| Second MF remote is the first in this repo — unproven config | De-risk early: make the `vite.config` `exposes` change + a trivial second module loadable before backend work commits to the shape; verify with `npm run build && npm run preview` (not dev server); fallback is a route within the existing remote if two `exposes` don't share singletons cleanly. |
| Roll-up N+1 blows the <200ms budget | Batch finders + review-exists projection + single in-memory RCDO tree walk; integration test asserts query count via Hibernate `Statistics`. |
| `PageImpl` serialization instability (Boot 3.3) | Return a `PageDto<T>` record, not raw `PageImpl`. |
| Closing the open review write breaks existing self-review tests | U2 re-owns plans to a report sub and flips `reviewer == OWNER` assertions to the manager; adds the self-review-403 case. |
| Outcome spread fails to differentiate against the real seed | U7 spreads demo commitments across both V3 Outcomes; AE4's "Ship v2"/"Retention" are illustrative — tests assert the real V3 Outcome titles. |
| Host JDK 25 breaks Gradle | Run backend checks with `JAVA_HOME=<JDK21>`. |
| Depends on D's not-yet-merged branch (stacked) | F branch is stacked on `feat/ic-lifecycle-frontend`; land F's MR after D's, rebase if D changes in review. |

---

## Documentation / Operational Notes

- After F lands, capture learnings inline in this plan's KTD/Risks (team convention — no `docs/solutions/`), especially the second-MF-remote findings.
- Update the PRD coverage roadmap (`docs/prd-coverage-roadmap.md`) to mark F's manager-dashboard + Pageable + manager-review-write requirements covered.
- Note for workstream G (E2E/deliverables): F adds the manager persona flows (board scan, drill-in review) to the Cypress/Gherkin suite.

---

## Sources & References

- **Origin document:** [docs/brainstorms/manager-dashboard-requirements.md](docs/brainstorms/manager-dashboard-requirements.md)
- Roadmap: `docs/prd-coverage-roadmap.md` (workstream F); Strategy: `STRATEGY.md` (Manager visibility track)
- Related code: `lifecycle/OwnedPlanLoader.java`, `lifecycle/ManagerReviewService.java`, `rcdo/RcdoService.java`, `frontend/src/store/api.ts`, `frontend/src/WeeklyCommitApp.tsx`, `frontend/vite.config.ts`
- Prior plans: `docs/plans/2026-06-02-001-feat-rcdo-hierarchy-plan.md` (seed/migration + Pageable deferral), `docs/plans/2026-06-03-002-feat-ic-lifecycle-frontend-plan.md` (RTK Query, MF, reuse targets)
