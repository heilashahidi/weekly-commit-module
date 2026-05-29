---
name: Weekly Commit
last_updated: 2026-05-29
---

# Weekly Commit Strategy

## Target problem

Weekly planning (15-Five) and strategic execution (the RCDO hierarchy) live in two
disconnected systems, so nothing forces an individual's weekly commitments to map to a
Supporting Outcome. Misalignment stays invisible until a manager manually reconstructs it —
too late to redirect the week. The crux is enforcement, not display: the strategic link is
optional today, so it doesn't happen.

## Our approach

Make the RCDO link a structural requirement of the weekly lifecycle rather than an optional
field — a commit cannot exist without a Supporting Outcome. The DRAFT → LOCKED → RECONCILING
→ RECONCILED state machine enforces planning and reconciliation discipline end to end, so
manager-level alignment visibility falls out automatically instead of being reconstructed by hand.

## Who it's for

**Primary:** People managers reviewing a team's weekly plans — they're hiring Weekly Commit to
see each week whether their team's actual work supports the right strategic outcomes, and to
catch misalignment while there's still time to redirect it.

**Secondary (load-bearing):** Individual Contributors (the 175+ employees) — they're hiring it
to plan their week and tie work to company priorities without it feeling like a tax. If linking
feels like busywork, the manager's roll-up becomes garbage.

## Key metrics

- **Alignment fidelity** — % of plans where the linked Outcome actually matched the work at reconciliation; DB
- **Weekly planning completion rate** — % of ICs who lock a plan each week; DB
- **Reconciliation accuracy** — planned vs. actual match at week close; DB
- **Manager review turnaround** — time from plan LOCKED → manager review complete; DB/events
- **Time-to-plan** — median minutes an IC spends planning, vs. the 15-Five baseline; analytics

## Tracks

### Weekly lifecycle

The state machine (DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward), mandatory RCDO
linking, chess-layer prioritization, and low-friction commit entry + reconciliation.

_Why it serves the approach:_ This is the approach — enforcement and the IC planning experience live here together.

### Manager visibility

The dashboard, team roll-up, and review flow.

_Why it serves the approach:_ Turns enforced links into real-time alignment insight for the primary persona.

### Platform foundation

Micro-frontend (Vite Module Federation remote), performance budgets, Auth0, Outlook Graph
integration, and test/coverage gates.

_Why it serves the approach:_ The prod-grade substrate; must live inside the PA host app at the required quality bars.

## Milestones

- 1-week build deliverables: Source Code, Technical Documentation, Demo Video, Test Results, AI Usage Log

## Not working on

- SSR frameworks (Next.js, Remix) — this is a client-side SPA
- CSS Modules or styled-components — Tailwind utility classes only
- Redux Saga or Thunk — RTK Query for all data fetching
- Prisma, TypeORM, or Sequelize — Spring Data JPA with Hibernate only
- Replicating PA's LogRocket/Loki monitoring or Nx package management (out of assessment scope)
