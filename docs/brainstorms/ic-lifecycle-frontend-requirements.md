---
date: 2026-06-03
topic: ic-lifecycle-frontend
workstream: D
depends_on: [A, B, C]
---

# IC Lifecycle Frontend (Workstream D)

## Summary

The Individual Contributor's primary weekly surface: a single adaptive **"My Week"** screen that renders the IC's current plan and changes what is shown and editable based on the lifecycle state (`DRAFT → LOCKED → RECONCILING → RECONCILED`), driving the full plan → lock → reconcile → carry loop that workstream C's backend enforces. The RCDO Supporting Outcome link is the unskippable, always-visible spine of every commitment — the design choice that makes strategic alignment a structural property of the screen rather than an optional field, which is the whole reason this replaces 15Five.

D is **full-stack**: it adds a thin backend slice (the missing plan-and-commitment *read* endpoints) to unblock the UI, then builds the IC screens on top.

---

## Problem Frame

Workstream C built the lifecycle engine and exposes it over JWT-secured REST, but there is no IC-facing UI — the engine has no hands. An IC currently cannot plan a week, lock it, reconcile it, or carry work forward except by raw API calls. D is the screen layer over the lifecycle flows F1–F4 defined in `docs/brainstorms/weekly-lifecycle-state-machine-requirements.md`. **D does not re-decide any lifecycle rule** (immutability, deadlines, the all-statused submit gate, carry-forward eligibility, the two-axis metrics) — those are C's. D surfaces them.

The product's reason to exist is enforcing the strategic link and making the weekly close real. The failure mode to avoid is shipping "15Five with extra steps" — a form where the RCDO link is just another optional box. The screen must make the link unskippable and the lifecycle gates feel consequential.

### Blocking dependency discovered during this brainstorm

C's API cannot currently render a plan's contents to a UI:
- `GET /api/lifecycle/plans/current` returns `WeeklyPlanDto`, which carries only `commitmentCount` — **not the commitments themselves**.
- There is **no endpoint to list a plan's commitments**, and no `GET` for a plan by id (only `current`).

The IC screen literally cannot display the commitment rows to add/edit/reconcile/view without these. C's own Tier-2 review flagged exactly these as "deferred to workstream D." So D owns adding them (the repository method `findByWeeklyPlanId` already exists). This is a real dependency, not scope creep — see Scope Boundaries.

---

## Actors

- **A1. Individual Contributor (IC)** — the sole actor for D. Plans the week, links each commitment to a Supporting Outcome, locks, reconciles at week's end, chooses carry-forwards, and sees any manager review left on the locked week. (~175+ users; carried from the lifecycle brainstorm.)
- A2 (people manager) and A3 (scheduler) are **not** D actors. The manager's review-writing UI is workstream F; the scheduler is C's backstop. D only *reads* a manager review if one exists.

---

## Key Flows

These map the lifecycle brainstorm's F1–F4 onto the adaptive "My Week" screen. The screen renders the current plan and adapts to its `status`.

- **F1 (D). Plan the week and lock.**
  - **Trigger:** IC opens "My Week"; the current plan is `DRAFT` (possibly pre-seeded with carry-overs from last week).
  - **Steps:** IC adds commitments. Each commitment row requires a Supporting Outcome link, chosen via the RCDO tree picker (reusing workstream B's tree) — the row cannot be saved without it. IC edits/deletes draft commitments freely. IC clicks **Lock**; the UI confirms the consequential action (planned commitments become immutable). An empty draft can still be locked but the UI surfaces the "no plan" outcome loudly.
  - **Outcome:** Plan is `LOCKED`; the screen flips to the locked view.
  - **Covers:** UX-R1, UX-R2, UX-R3, UX-R4; AE-D1, AE-D2, AE-D3.

- **F2 (D). Work the locked week.**
  - **Trigger:** Plan is `LOCKED`.
  - **Steps:** Planned commitments are shown visibly frozen (read-only, immutability is legible — not just an error on edit attempt). The IC can append **unplanned** commitments, each still requiring a Supporting Outcome link and visibly flagged unplanned. The status-deadline / auto-advance countdown is shown. If a manager review exists, its comment is displayed read-only.
  - **Outcome:** A locked plan that may contain frozen planned + appended unplanned commitments.
  - **Covers:** UX-R5, UX-R6, UX-R7, UX-R12; AE-D4, AE-D5, AE-D9.

- **F3 (D). Reconcile and close.**
  - **Trigger:** Plan is `RECONCILING` (the IC started reconciliation, or the backstop advanced it).
  - **Steps:** Each commitment (planned and unplanned) gets a status — `DONE` / `PARTIAL` / `NOT_DONE` / `DROPPED` — plus an optional note. The UI shows planned intent next to the outcome being set. **Submit** is disabled until every commitment is statused; the UI makes the remaining-unstatused count visible. On submit the plan becomes `RECONCILED`.
  - **Outcome:** Plan is `RECONCILED`; the screen shows the planned-vs-actual summary and the two-axis metrics (reconciliation-accuracy + planned-vs-unplanned ratio). Any `UNRECONCILED` (system-applied on auto-close) commitments are shown honestly, not as done.
  - **Covers:** UX-R8, UX-R9, UX-R10; AE-D6, AE-D7, AE-D10.

- **F4 (D). Carry unfinished work forward.**
  - **Trigger:** Plan is `RECONCILED`; carry candidates exist.
  - **Steps:** The UI lists carry candidates (planned commitments marked `PARTIAL` / `NOT_DONE`, pre-linked to their original Outcome; `DROPPED` and `DONE` are not offered). Each shows its accumulated week-count. The IC selects which to carry; the UI confirms they will seed next week's `DRAFT`.
  - **Outcome:** Selected candidates are carried into next week's `DRAFT`, visibly marked as carry-overs with incremented week-count.
  - **Covers:** UX-R11; AE-D8.

---

## Requirements

UX-prefixed requirements are D's; each surfaces one or more of C's R-numbers without re-deciding them.

**Adaptive screen & lifecycle legibility**
- UX-R1. A single "My Week" screen renders the IC's current plan and adapts its controls and display to the plan `status` (`DRAFT` / `LOCKED` / `RECONCILING` / `RECONCILED`). The lifecycle state drives what is shown and editable; there is no separate per-phase navigation.
- UX-R2. The current lifecycle state is always visible, and the status-deadline (when the backstop will auto-advance) is surfaced whenever one is set.
- UX-R3. The four lifecycle states and the five reconciliation statuses each read consistently (consistent status styling) so state is legible at a glance.

**RCDO link as the spine (the core bet)**
- UX-R4. A commitment cannot be created or saved from the UI without a Supporting Outcome link selected via the RCDO tree picker (surfaces C's R6). The picker reuses workstream B's RCDO tree. The link is a required, first-class part of the add/edit interaction, not an optional field.
- UX-R5. Every commitment row always displays its linked Supporting Outcome (and ideally its ancestry context), so strategic alignment is the default visual, not hidden behind an edit.

**Plan / lock (DRAFT)**
- UX-R6. In `DRAFT`, the IC can add, edit, and delete commitments. Lock is presented as a deliberate, consequential action with clear before/after (planned commitments will freeze).
- UX-R7. Locking an empty draft is allowed but the UI surfaces the resulting "no plan" outcome loudly (surfaces C's R5), rather than treating it as a silent normal save.

**Locked week (LOCKED)**
- UX-R8. In `LOCKED`, planned commitments are shown visibly immutable (legibly frozen, not merely erroring on edit) — surfaces C's R7. The IC can append unplanned commitments, each requiring a link and visibly flagged `unplanned` (surfaces C's R8).

**Reconciliation (RECONCILING → RECONCILED)**
- UX-R9. In `RECONCILING`, the IC sets each commitment's status (`DONE` / `PARTIAL` / `NOT_DONE` / `DROPPED`) with an optional note. The UI never lets the IC set `UNRECONCILED` (system-only).
- UX-R10. Submit is blocked until every commitment (planned + unplanned) has a status; the UI shows how many remain (surfaces C's R10). On submit the plan advances to `RECONCILED`.
- UX-R11. After `RECONCILED`, the UI shows a planned-vs-actual summary and the two-axis metrics from C's metrics endpoint (reconciliation-accuracy and planned-vs-unplanned ratio), and shows any `UNRECONCILED` commitments honestly (surfaces C's R11, R19).

**Carry forward (RECONCILED)**
- UX-R12. After `RECONCILED`, the UI lists carry candidates (C's carry-candidates endpoint), shows each candidate's week-count, lets the IC select a subset, and carries them into next week's `DRAFT` (surfaces C's R12–R15). `DROPPED` items are not offered.

**Manager review (read-only)**
- UX-R13. If a manager review exists on the plan, its comment is displayed read-only to the IC (the IC-facing half of C's R16). D builds no review-writing UI.

**Cross-cutting frontend requirements**
- UX-R14. All data access uses RTK Query. Every mutation (add/edit/delete commitment, lock, start-reconciling, set status, submit, carry) declares `invalidatesTags` so the adaptive "My Week" view refetches and re-renders the correct state after each action. C's read endpoints `providesTags` accordingly.
- UX-R15. Every view has explicit loading, empty, and error states. Errors surface C's RFC 7807 ProblemDetail messages (C enabled `problemdetails`) rather than bare failures — e.g. an illegal action or ownership error shows the reason.
- UX-R16. Routes are lazy-loaded for sub-second initial render (PRD performance benchmark), consistent with the Module Federation remote structure from workstream A.
- UX-R17. The module remains a Module Federation remote with a single exposed entry (workstream A's pattern); D adds screens behind the existing lazy boundary, it does not add new remote entry points.

**Backend slice (D owns, to unblock itself)**
- UX-R18. Add `GET /api/lifecycle/plans/{planId}/commitments` returning the plan's commitments (planned + unplanned), ownership-checked like every other lifecycle endpoint. This is the endpoint the "My Week" view reads to render commitment rows.
- UX-R19. Add a way to fetch a plan by id for non-current weeks (e.g. `GET /api/lifecycle/plans/{planId}`), so a just-seeded next-week draft or a past week is addressable beyond `current`. (Planning may decide the exact shape; the requirement is that the UI can fetch a specific plan, not only the current one.)

---

## Acceptance Examples

- **AE-D1. Covers UX-R4 / C-R6.** Given the IC is adding a commitment, when they try to save it without selecting a Supporting Outcome, then the save control is unavailable / the save is rejected and no commitment is created.
- **AE-D2. Covers UX-R4, UX-R5.** Given the IC opens the RCDO picker, when they select a Supporting Outcome, then the commitment row shows that Outcome and the commitment can be saved.
- **AE-D3. Covers UX-R6, UX-R7.** Given a `DRAFT` with zero commitments, when the IC locks, then the UI warns/confirms and the resulting plan is shown in a distinct "no plan" locked state.
- **AE-D4. Covers UX-R8 / C-R7.** Given a `LOCKED` plan, when the IC views a planned commitment, then it is displayed as immutable (no edit/delete controls), not merely erroring when edited.
- **AE-D5. Covers UX-R8 / C-R8.** Given a `LOCKED` plan, when the IC adds a new commitment with a valid Supporting Outcome link, then it is accepted, shown flagged `unplanned`, and the planned commitments remain frozen.
- **AE-D6. Covers UX-R9, UX-R10 / C-R10.** Given a plan in `RECONCILING` where one commitment has no status, when the IC attempts to submit, then submit is blocked and the UI shows that one commitment remains unstatused.
- **AE-D7. Covers UX-R10.** Given every commitment is statused, when the IC submits, then the plan advances to `RECONCILED` and the summary view is shown.
- **AE-D8. Covers UX-R12 / C-R12, R14, R15.** Given a `RECONCILED` plan with one `PARTIAL`, one `NOT_DONE`, and one `DROPPED` commitment, when the IC opens carry-forward, then the `PARTIAL` and `NOT_DONE` are offered (with week-count) and the `DROPPED` is not; selecting them carries them into next week's `DRAFT`.
- **AE-D9. Covers UX-R13 / C-R16.** Given a `LOCKED` plan a manager has reviewed, when the IC views the week, then the manager's comment is displayed read-only.
- **AE-D10. Covers UX-R11 / C-R11, R19.** Given a `RECONCILED` week with planned and unplanned commitments (including an `UNRECONCILED` one), when the IC views the summary, then reconciliation-accuracy and the planned-vs-unplanned ratio are shown, and the `UNRECONCILED` commitment is shown as such (not as done).
- **AE-D11. Covers UX-R15.** Given an action fails (e.g. an ownership or illegal-state error), when the API returns a ProblemDetail, then the UI shows the human-readable reason, not a bare error.

---

## Success Criteria

- An IC can run the entire weekly cycle — plan, link every commitment to a Supporting Outcome, lock, reconcile, carry forward — end to end through the UI, without touching the raw API.
- The Supporting Outcome link is impossible to skip and always visible: there is no way to create an unaligned commitment, and alignment is the default visual on every row.
- The lifecycle state is unmistakable: at any moment the IC can tell what state their week is in, what they can do, and when it will auto-advance.
- The weekly close produces a visible planned-vs-actual truth, including honest display of auto-closed (`UNRECONCILED`) work.
- A downstream implementer can build every screen, state, and interaction from this document plus C's API without inventing product behavior.

---

## Scope Boundaries

### In scope
- The adaptive "My Week" IC screen across all four lifecycle states, the full plan → lock → reconcile → carry loop.
- The RCDO Supporting Outcome picker (reusing workstream B's tree) as the required link interaction.
- Read-only display of a manager review if one exists.
- RTK Query wiring for all C lifecycle endpoints with cache invalidation on mutations.
- Loading/empty/error states and ProblemDetail-aware error display.
- Lazy-loaded routes behind the existing Module Federation remote entry.
- **Backend slice:** the plan-commitments list endpoint (UX-R18) and a fetch-plan-by-id capability (UX-R19), ownership-checked, mirroring C's controller/service/DTO conventions.

### Deferred for later
- A formal design system / `DESIGN.md`. D builds on Tailwind + Flowbite defaults with consistent status styling; a design consultation can come later if the product needs a stronger visual identity.
- Optimistic UI updates / real-time deadline countdown ticking — a simple displayed deadline and refetch-on-action is sufficient first; live ticking is polish.
- Notifications/reminders around deadlines (the lifecycle brainstorm already defers these).

### Outside this product's identity (this workstream)
- The **manager dashboard / team roll-up UI** (workstream F). D is IC-facing only; it never builds manager-side review-writing or team views.
- The **chess layer** categorization/prioritization (workstream E).
- Any change to C's **lifecycle rules** (states, transitions, immutability, deadlines, metric semantics). D consumes them; it does not alter them. The only backend additions are the read endpoints D needs to render (UX-R18/R19), which add no new lifecycle behavior.
- **RCDO hierarchy management** (creating/editing strategy nodes). D reuses B's read-only tree.

---

## Key Decisions

- **One adaptive "My Week" view over per-phase routes.** The plan is one entity that moves through states, so the state *is* the navigation; the screen adapts rather than making the IC walk a wizard. Keeps "one plan, one place" and avoids a steps-y feel. (Reconciliation and carry are heavier sub-interactions but live within the same view's `RECONCILING`/`RECONCILED` modes.)
- **RCDO link as the unskippable spine.** Chosen as the single most important structural signal: you cannot add a commitment without a Supporting Outcome, and the link is always visible on the row. This is the direct UI answer to "why isn't this 15Five" — alignment is enforced by the interaction, not requested by a field.
- **D is full-stack (adds the read endpoints).** The IC screen cannot render without a way to read a plan's commitments, which C does not expose. Rather than block on a separate ticket or distort the plan DTO, D adds the small read slice it needs (C's review already earmarked this for D). The endpoints add no lifecycle behavior — pure reads, ownership-checked.
- **Embed-vs-list resolved toward a list endpoint, not a fatter plan DTO.** A separate `GET .../commitments` keeps plan-fetch and commitment-fetch independently cacheable/invalidatable in RTK Query and leaves C's existing `WeeklyPlanDto` contract intact. (Final endpoint shape is a planning detail.)
- **Manager review is read-only in D.** Surfacing the comment is the IC-facing half of the non-blocking-review rule; writing reviews belongs to the manager surface (F). Cheap to include, real value (the IC sees feedback C already stores).
- **Tailwind/Flowbite defaults, no design system yet.** Right-sized for a first functional pass; consistent status styling gives enough visual system for state legibility without the cost of a full design consultation.
- **Full loop ships as one unit.** A half-loop (plan+lock only) isn't a usable weekly tool and defers exactly the enforcement payoff (planned-vs-actual) that justifies the product. Matches how C shipped the whole lifecycle.

---

## Dependencies / Assumptions

- **Workstream C (merged to `main`)** provides the lifecycle endpoints D drives. D adds only the two read endpoints (UX-R18/R19); all mutating/transition endpoints already exist.
- **Workstream B (merged)** provides the RCDO read API and tree component D reuses for the link picker. *(Assumption: B's tree viewer is reusable as a selectable picker, or can be adapted with modest effort; planning to confirm.)*
- **Workstream A (merged)** provides the Vite/React/RTK Query/Tailwind/Flowbite substrate, the Module Federation remote structure, and in-memory JWT token injection D builds within.
- **Assumption:** the IC's identity for "my current plan" comes from the JWT principal as C already models it (`GET /plans/current` resolves the owner from the token); D does not introduce a separate user/identity concept.
- **Assumption:** C's reconciliation/metrics/carry endpoints return enough per-commitment detail (planned flag, status, note, carry lineage, week-count) for the views above — the existing `CommitmentDto` and `PlanMetricsDto` appear sufficient; planning to confirm against each view's needs.

---

## Outstanding Questions

### Deferred to Planning

- [Affects UX-R18/R19][Technical] Exact shape of the read endpoints — a flat `GET .../plans/{id}/commitments` plus `GET .../plans/{id}`, versus a single composite "plan with commitments" read. Resolve in planning against RTK Query cache/invalidation ergonomics.
- [Affects UX-R4/R5][Technical] Whether B's existing `RcdoTree` component is reused directly as a picker or a selectable variant is built alongside it, and how much ancestry context a committed row shows.
- [Affects UX-R11] Exact composition of the planned-vs-actual summary (per-commitment table vs aggregate-first) — a presentation decision for planning/design.
- [Affects UX-R2] Whether the status-deadline is shown as an absolute time or a relative countdown (and if the latter, static vs ticking — ticking is deferred polish).
