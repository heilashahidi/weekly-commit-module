---
date: 2026-05-29
topic: weekly-lifecycle-state-machine
---

# Weekly Lifecycle State Machine

## Summary

A per-IC, per-week state machine (`DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward`)
that makes RCDO-linked weekly planning structural: commits can't exist without a Supporting
Outcome, the weekly cadence advances on a hybrid manual/deadline model, locked plans are
immutable but appendable, reconciliation is a structured per-commit pass, and unfinished work
carries forward across weeks with its history intact.

---

## Problem Frame

Weekly planning (in 15Five) and strategic execution (the RCDO hierarchy — Rally Cries,
Defining Objectives, Outcomes) live in two disconnected systems. Nothing forces an IC's
weekly commitments to map to a Supporting Outcome, and nothing forces the week to actually
close — so misalignment stays invisible until a manager reconstructs it by hand, by which
point the week (or the quarter) has already pointed the wrong way. The cost lands hardest on
people managers across 175+ employees, who review weekly plans without knowing whether the
work supports the right priorities, with no real-time signal and no way to redirect in time.

The lifecycle is where enforcement either happens or doesn't: if the states are advisory and
plans can drift to match reality, the system degrades back into 15Five. The hard part is
making the strategic link and the weekly close *structural properties of the lifecycle* rather
than optional fields people remember to fill in.

---

## Actors

- A1. Individual Contributor (IC): plans the week, links commits to Outcomes, locks the plan, reconciles at week's end, chooses carry-forwards. ~175+ users.
- A2. People manager: reviews a team's locked plans during the week (non-blocking), surfaces misalignment in time to redirect. Primary persona.
- A3. System / scheduler: enforces the deadline backstop — auto-locks plans, auto-advances states when actors don't act in time.

---

## Key Flows

- F1. Plan the week and lock
  - **Trigger:** A new week opens; the IC's plan is in `DRAFT` (possibly pre-seeded with carry-forwards).
  - **Actors:** A1, A3
  - **Steps:** IC adds commits; each commit must be linked to a Supporting Outcome before it can be saved. IC locks the plan (`DRAFT → LOCKED`). If the IC hasn't locked by the deadline, the scheduler auto-locks whatever is in `DRAFT`.
  - **Outcome:** Plan is `LOCKED`, flagged either `user-locked` or `auto-locked`; an empty auto-locked plan is recorded as a distinct "no plan" outcome.
  - **Covered by:** R3, R4, R5, R6, R7

- F2. Work the locked week (with mid-week reality)
  - **Trigger:** Plan is `LOCKED`; the work week is underway.
  - **Actors:** A1, A2
  - **Steps:** IC executes against locked commits (which can't be edited or deleted). When unplanned work arises, the IC adds new commits flagged `unplanned`, still requiring an Outcome link. In parallel, the manager reviews the locked plan and may leave a comment; review never blocks the IC.
  - **Outcome:** A locked plan that may contain both planned (immutable) and unplanned (appended) commits, optionally carrying a manager review annotation.
  - **Covered by:** R7, R8, R16, R17

- F3. Reconcile and close
  - **Trigger:** End of the work week; plan enters `RECONCILING` (manual or deadline-driven).
  - **Actors:** A1, A3
  - **Steps:** IC assigns each commit a status (`Done` / `Partial` / `Not done` / `Dropped`) and an optional note. Once every commit is statused, the IC submits (`RECONCILING → RECONCILED`).
  - **Outcome:** Plan is `RECONCILED`; per-commit actuals are recorded; planned-vs-actual and reactive-ratio signals are computable.
  - **Covered by:** R9, R10, R11

- F4. Carry unfinished work forward
  - **Trigger:** Plan reaches `RECONCILED`; the next week's `DRAFT` is being seeded.
  - **Actors:** A1
  - **Steps:** Planned commits marked `Partial` or `Not done` are offered as carry candidates, pre-linked to their original Outcome. The IC selects which to carry. `Dropped` items are not offered.
  - **Outcome:** Next week's `DRAFT` contains chosen carry-overs, each marked as a carry-over with its history and accumulated week-count.
  - **Covered by:** R12, R13, R14, R15

---

## Requirements

**State machine & transitions**
- R1. The lifecycle has five states — `DRAFT`, `LOCKED`, `RECONCILING`, `RECONCILED`, `Carry Forward` — scoped per IC, per week. `Carry Forward` is the cross-week handoff that seeds the next week's `DRAFT`.
- R2. Transitions follow a hybrid model: an actor advances a state manually when ready, and a time-based backstop auto-advances any plan whose actor hasn't acted by the relevant deadline.
- R3. `DRAFT → LOCKED` happens when the IC locks manually, or automatically at the lock deadline if the IC hasn't.
- R4. The system records whether a transition was actor-initiated or backstop-initiated (e.g., `user-locked` vs `auto-locked`), and this distinction is available to managers and metrics.
- R5. If a `DRAFT` is empty at the lock deadline, it still locks, but as a distinct "no plan submitted" outcome that is surfaced loudly to the manager and counts as an incomplete plan.

**RCDO linking & enforcement**
- R6. A commit cannot be created or saved without being linked to a Supporting Outcome in the RCDO hierarchy. The link is mandatory at entry, not an optional field.
- R7. Once a plan is `LOCKED`, its planned commits are immutable — they cannot be edited or deleted.
- R8. New commits can be added to a `LOCKED` plan; each is flagged `unplanned` and still requires an Outcome link.

**Reconciliation**
- R9. In `RECONCILING`, each commit receives a status — `Done`, `Partial`, `Not done`, or `Dropped` — plus an optional free-text note.
- R10. A plan advances `RECONCILING → RECONCILED` only when every commit (planned and unplanned) has a status; the IC submits to confirm.
- R11. Unplanned commits are reconciled like planned ones, but are excluded from the reconciliation-accuracy measure (planned-vs-actual) and instead feed the planned-vs-unplanned ratio.

**Carry forward**
- R12. When a plan reaches `RECONCILED`, planned commits marked `Partial` or `Not done` become carry candidates for the next week's `DRAFT`, pre-linked to their original Supporting Outcome.
- R13. Carry-forward is IC-selected, not automatic — the IC chooses which candidates to carry.
- R14. A carried commit is visibly marked as a carry-over and retains its history, including an accumulated week-count of how many weeks it has rolled over.
- R15. Commits marked `Dropped` are not offered as carry candidates.

**Manager review**
- R16. Manager review is a non-blocking overlay on a plan — an attribute capturing reviewer, timestamp, and optional comment — whose primary touchpoint is the `LOCKED` week.
- R17. Manager review never gates a lifecycle transition; the state machine advances independently of whether review has occurred.

**Deadline backstops**
- R18. Every forward transition has a time-based backstop, not just lock: `DRAFT → LOCKED`, `LOCKED → RECONCILING`, and `RECONCILING → RECONCILED` each auto-advance at their deadline if the responsible actor hasn't acted, so a plan can never stall mid-lifecycle.
- R19. When `RECONCILING → RECONCILED` is auto-advanced by the backstop, any commit the IC never statused is recorded as `Unreconciled` — a distinct system-applied status, never silently `Done` — so an auto-closed week stays an honest, visible signal rather than fake completion.

---

## Acceptance Examples

- AE1. **Covers R3, R4.** Given an IC's plan is in `DRAFT` with two linked commits, when the lock deadline passes without the IC locking, then the plan transitions to `LOCKED` and is flagged `auto-locked`.
- AE2. **Covers R5.** Given an IC's `DRAFT` has zero commits, when the lock deadline passes, then the plan locks as a distinct "no plan submitted" outcome, is surfaced on the manager view, and counts against the weekly planning completion rate.
- AE3. **Covers R6.** Given an IC is adding a commit, when they attempt to save it without a Supporting Outcome link, then the save is rejected and the commit is not created.
- AE4. **Covers R7, R8.** Given a `LOCKED` plan, when the IC tries to edit a planned commit it is disallowed, but when they add a new commit it is accepted and flagged `unplanned` (still requiring an Outcome link).
- AE5. **Covers R10.** Given a plan in `RECONCILING` where one commit has no status, when the IC attempts to submit, then the transition to `RECONCILED` is blocked until every commit is statused.
- AE6. **Covers R11.** Given a reconciled week with 4 planned commits (3 `Done`) and 2 `unplanned` commits, when accuracy is computed, then reconciliation-accuracy is based on the 4 planned commits only, and the 2 unplanned commits contribute to the planned-vs-unplanned ratio.
- AE7. **Covers R12, R14, R15.** Given a `RECONCILED` plan with one `Partial`, one `Not done`, and one `Dropped` commit, when the next week's `DRAFT` is seeded, then the `Partial` and `Not done` commits are offered as carry candidates marked as carry-overs with incremented week-count, and the `Dropped` commit is not offered.
- AE8. **Covers R16, R17.** Given a `LOCKED` plan a manager has not yet reviewed, when the week progresses to `RECONCILING`, then the transition succeeds regardless of review status, and review remains available as an annotation.
- AE9. **Covers R18, R19.** Given a plan in `RECONCILING` where the IC has statused 2 of 4 commits, when the reconciliation deadline passes, then the plan auto-advances to `RECONCILED` and the 2 unstatused commits are recorded as `Unreconciled` (not `Done`).

---

## Success Criteria

- A manager can tell, during the work week (not after it), whether each team member's locked plan aligns to the right Outcomes — early enough to redirect.
- Every IC's week is accounted for each cycle: there are no indefinitely-open plans; missed planning shows up as a tracked "no plan" outcome rather than a silent gap.
- Planned-vs-actual and planned-vs-unplanned signals are computable directly from reconciliation data without manual interpretation.
- A downstream planner can implement the state machine, its transitions, and its enforcement rules from this doc without having to invent lifecycle behavior, edge-case handling, or metric semantics.

---

## Scope Boundaries

- The manager dashboard / team roll-up UI (Track 2) — this brainstorm defines the lifecycle engine and the data it produces, not the visualization surface.
- The "chess layer" categorization and prioritization mechanism — referenced in the PRD, lives in `DRAFT`, but its design is a separate concern.
- RCDO hierarchy management (creating/editing Rally Cries, Defining Objectives, Outcomes) — the lifecycle consumes this hierarchy; it does not manage it.
- Outlook Graph / calendar integration — out of scope for the lifecycle core.
- Exact deadline values and cadence calendar (e.g., which day/time locking closes) — a configuration concern, not a lifecycle-shape decision.
- Notifications/reminders around deadlines — adjacent and deferrable; not required for the state machine to be correct.

---

## Key Decisions

- Hybrid transitions over pure-manual or pure-automatic: manual control preserves agency, while the deadline backstop makes cadence structural so enforcement doesn't depend on people remembering.
- Manager review as a non-blocking overlay rather than a hard gate: review must happen *during* the locked week to be redirectable (the strategy's core promise), and gating the IC on manager latency would stall the cadence and punish the IC.
- Locked plans immutable but appendable: immutability is what makes planned-vs-actual falsifiable (the enforcement), while allowing flagged `unplanned` additions captures emergent work honestly instead of forcing drift or omission.
- Empty plans lock as a distinct, loud "no plan" outcome rather than a silent blank: a planning gap is exactly the misalignment the product exists to catch, so it must be tracked, not buried.
- Carry-forward is IC-selected and excludes `Dropped`: auto-rolling everything would clutter plans with consciously-abandoned work; "dropped" is a deliberate stop, distinct from "didn't finish."
- Two-axis reconciliation: keeping unplanned work out of reconciliation-accuracy preserves a clean "did the plan come true?" signal, while the planned-vs-unplanned ratio captures "how reactive was the week?" separately.
- Deadline backstops on every forward transition, not just lock: a backstop only at lock would let plans stall in `LOCKED` or `RECONCILING` forever, reopening the "indefinitely-open plan" hole; auto-closure records `Unreconciled` rather than `Done` so it stays honest.

---

## Dependencies / Assumptions

- The RCDO hierarchy (Rally Cries → Defining Objectives → Outcomes → Supporting Outcomes) exists as a linkable structure that commits can reference. *(Unverified — no code exists yet; the RCDO model is described in the PRD but not built.)*
- A scheduling mechanism is available for the deadline backstop. The PRD lists AWS SQS/SNS and the stack is Spring Boot, which supports scheduled jobs; the specific mechanism is a planning decision.
- The lifecycle is scoped per IC per week; "the week" is assumed to be a fixed organization-wide cycle rather than per-user custom periods.

---

## Outstanding Questions

### Deferred to Planning

- [Affects R1][Technical] Whether `Carry Forward` is modeled as a distinct persisted state or as the seeding action that produces the next week's `DRAFT`.
- [Affects R4, R5][Technical] How the `auto-locked` / `no plan` distinctions are represented so both managers and metrics can consume them.
- [Affects R16][Technical] Where the manager-review annotation lives relative to the plan entity, given the plan's planned commits are immutable after lock.
