---
date: 2026-06-03
topic: manager-dashboard
---

# Manager Dashboard + Team Roll-up (Workstream F)

## Summary

A manager-dashboard micro-frontend remote whose home screen is a current-week status board across a manager's direct reports — each row shows lifecycle status, the RCDO outcome spread the report's week points at, and review state. The manager drills into any report's plan read-only and writes the non-blocking review. Backed by a seeded reporting model and manager-scoped authorization. Historical analytics are out of v1.

---

## Problem Frame

People managers are the primary persona for Weekly Commit (`STRATEGY.md`): they hire it to see, each week, whether their team's actual work supports the right strategic outcomes — and to catch misalignment while there's still time to redirect it. Today that visibility does not exist. Workstreams A–C built the lifecycle engine and the data (plans, RCDO-linked commitments, reconciliation, a manager-review annotation), and D built the IC's own "My Week" screen, but there is no manager-facing surface at all. A manager cannot answer "who on my team has planned this week, and is their week pointed at the right priorities?" without reading each IC's plan by hand — the exact manual reconstruction the product exists to remove.

Two specific gaps make this acute. There is no model of who manages whom (principals are opaque auth identities, no user/team/role table exists), so the system cannot even scope a "team." And the manager-review backend that C shipped is deliberately unguarded: any authenticated principal may review any plan, because the org model it needs did not exist yet.

---

## Actors

- A1. People manager (primary): opens the dashboard, scans the team's current week, drills into a report's plan, writes/updates a review.
- A2. Individual Contributor / report: owns the weekly plan F reads. Does not use F directly; their plan, RCDO links, and reconciliation are the data the board surfaces.
- A3. Lifecycle engine (workstream C): produces plans, statuses, commitments, RCDO links, reconciliation metrics, and the review record. F reads these; it never re-decides a lifecycle rule.

---

## Key Flows

- F1. Scan the current-week team board
  - **Trigger:** A manager opens the dashboard.
  - **Actors:** A1, A3
  - **Steps:** System resolves the manager's direct reports → for each, loads the current-week plan (or marks "no plan yet") → renders one row per report with status, RCDO outcome spread, and review-done indicator.
  - **Outcome:** The manager can tell at a glance who has planned, who hasn't, and where each report's week is pointed.
  - **Covered by:** R1, R3, R6, R7, R8, R9, R11

- F2. Drill into a report's plan and review it
  - **Trigger:** The manager clicks a report's row.
  - **Actors:** A1, A3
  - **Steps:** System authorizes manager-of-owner → opens a read-only view of the report's plan (commitments, linked Supporting Outcomes, planned-vs-actual when reconciled) → manager writes or updates the single review comment → reviewer + timestamp shown.
  - **Outcome:** The manager has read the plan and left a non-blocking review without altering the IC's plan.
  - **Covered by:** R3, R4, R5, R12, R13, R14

---

## Requirements

**Org model & authorization**
- R1. The system models a manager→direct-reports mapping, seeded via migration (not self-service), keyed by principal identity. A principal is a manager iff they have at least one report.
- R2. The mapping carries a human-readable display name per report so the board never shows opaque principal ids.
- R3. A manager may read the plans, commitments, and reviews of their direct reports only; requests against non-reports are denied. This extends D's current owner-only plan read to "owner OR manager-of-owner."
- R4. Creating or updating a manager review is restricted to the reviewed plan owner's manager; C's currently-open write path (any authenticated principal) is closed.
- R5. Reviews remain non-blocking: writing or omitting a review never affects the lifecycle state machine (preserves C's R17).

**Team status board**
- R6. The dashboard home is a current-week status board, one row per direct report.
- R7. Each row shows the report's current-week plan lifecycle status (DRAFT / LOCKED / RECONCILING / RECONCILED), or a distinct "no plan yet" state when the report has not started a plan for the current week.
- R8. Each row shows the RCDO outcome spread for that report's current-week commitments — which Outcomes / Rally Cries the week's work points at, with counts.
- R9. Each row shows whether a manager review exists for that plan (review-done indicator).
- R10. Each row links to the report's plan detail (F2).
- R11. The board includes reports with no current-week plan; absence is a signal, not an omitted row.

**Plan detail & review**
- R12. From a row, the manager opens a read-only view of the report's plan: commitments, their linked Supporting Outcomes, and planned-vs-actual when reconciled. No edit affordances.
- R13. The manager can create or update the single non-blocking review comment for that plan from the detail view, and see the reviewer and timestamp.
- R14. Review writing is available whenever the plan exists (no lifecycle-status gate), consistent with the existing upsert semantics.

**Platform & performance**
- R15. The manager dashboard ships as its own micro-frontend remote (Vite Module Federation), lazy-loaded, consistent with the platform remote pattern.
- R16. The team roll-up query supports pagination (Spring Data Pageable) sized for up to 2000 records, per the PRD performance budget.
- R17. Plan retrieval stays within the platform latency budget (<200ms for plan retrieval); reuse C's read paths rather than new heavy queries where possible.
- R18. F reuses D's RTK Query types and status styling (`statusStyles` / `StatusBadge`) rather than re-deriving them.

---

## Acceptance Examples

- AE1. **Covers R3.** Given manager M with report I and non-report J, when M requests I's plan it returns; when M requests J's plan, access is denied.
- AE2. **Covers R4.** Given a plan owned by I whose manager is M, when M writes a review it succeeds; when a principal who is not I's manager writes a review, it is denied.
- AE3. **Covers R7, R11.** Given report I has not created a plan for the current week, when M opens the board, I's row is present and shows "no plan yet."
- AE4. **Covers R8.** Given I's current-week plan has 3 commitments linked under Rally Cry "Ship v2" and 1 under "Retention," when M views the board, I's row shows the spread `Ship v2 ×3`, `Retention ×1`.

---

## Board sketch

```
THIS WEEK             status         outcome spread              review
Ava    ● LOCKED       [Ship v2 ×3] [Retention ×1]               —
Ben    ○ DRAFT        [Internal tools ×2]                       —
Cleo   ● RECONCILED   [Ship v2 ×5]                              ✓
Dan    ◌ no plan yet  —                                          —
        └─ click a row → read-only plan detail + write review
```

---

## Success Criteria

- In one screen, a manager can tell within the current week which reports have planned, which haven't, and where each report's week is pointed — and act (review or redirect) before the week closes.
- Manager review turnaround (LOCKED → review complete), a `STRATEGY.md` key metric, is observable from this flow.
- ce-plan can implement without inventing product behavior: the org-model shape, the who-sees-what rules, the board's columns and states, and the remote boundary are all specified here.

---

## Scope Boundaries

- Historical metrics / trends dashboard (alignment fidelity over time, completion rate, review-turnaround charts) — deferred to a later pass.
- Transitive / skip-level org roll-up; v1 shows direct reports only.
- Self-service org assignment or any org-admin UI; the reporting mapping is seeded.
- A first-class User entity; F uses principal identity plus a seeded reporting mapping.
- Chess-layer categorization / prioritization (workstream E).
- Editing a report's plan or commitments; F is read-only over IC data, plus review write.
- Outlook Graph integration / notifications for review nudges (out of assessment scope).

---

## Key Decisions

- Seeded reporting mapping over a real User entity: smallest scope, demo-ready, no Auth0 coupling, testable in CI without a live IdP. Trade-off accepted — assignment is migration-only.
- Current-week status board over a metrics/trends dashboard: matches the primary persona's "redirect in time" job. Metrics are post-hoc and deferred.
- Alignment surfaced as RCDO outcome spread, not a link-completeness ratio: links are structurally mandatory (C's R6), so a ratio is always 100% and tells the manager nothing. The real signal is which outcomes the week targets.
- Manager-scoped authorization grounded in the new org model: closes C's wide-open review write and extends D's owner-only plan read to owner-or-manager. This is the auth-asymmetry fix the prior session flagged, now with a model behind it.
- "No plan this week" is a first-class board state: a report who didn't plan is the highest-value thing for a manager to catch.

---

## Dependencies / Assumptions

- Depends on workstream C (lifecycle engine + the `ManagerReview` backend) — merged to main.
- Depends on workstream B (RCDO hierarchy) to resolve a commitment's Outcome / Rally Cry ancestors for the outcome spread (R8).
- Stacked on workstream D (IC frontend): F reuses D's RTK Query types and status styling, and F's branch builds on D's not-yet-merged branch.
- Assumption: a "current week period" is identifiable from C's lifecycle model so the board can select each report's current plan. [verify in planning]
- Assumption: the principal identity used in the reporting mapping matches the principal C stamps as plan owner, so manager → report → plan-ownership joins line up. [verify in planning]

---

## Outstanding Questions

### Resolve Before Planning

- None blocking. The keystone (org model) is decided; remaining items are technical and better answered during planning.

### Deferred to Planning

- [Affects R8][Technical] At which RCDO level is the spread grouped — Supporting Outcome, Outcome, or Rally Cry? Pick the level that reads as "priority" to a manager; confirm against seeded RCDO data.
- [Affects R1, R17][Technical] Is the board one roll-up query or N per-report reads, and does that stay within the latency budget?
- [Affects R12][Technical] How much of D's view components can be reused read-only vs. a thin manager-specific detail view.
- [Affects R16][Technical] Pagination sizing for the direct-reports roll-up — direct-report teams are small, so Pageable here is mostly satisfying the perf-ceiling requirement; confirm defaults.
- [Affects assumptions][Technical] Confirm current-week-period derivation and principal-identity alignment noted under Assumptions.
