---
date: 2026-05-29
topic: prd-coverage-roadmap
---

# PRD Coverage Roadmap

Decomposes the full PRD (`PRD-ST6.md`) into a set of plans so every requirement is
accounted for before any single plan is written. The weekly-lifecycle brainstorm
(`docs/brainstorms/weekly-lifecycle-state-machine-requirements.md`) covers only one slice
(Plan C below).

This is a map, not an implementation plan. Each workstream becomes its own `ce-plan` (some
need a `ce-brainstorm` first — flagged below).

---

## Workstreams (plans)

| ID | Workstream | Strategy track | Needs brainstorm first? | Depends on |
|----|-----------|----------------|--------------------------|------------|
| **A** | Platform foundation & scaffolding | Platform foundation | No — config-driven | — |
| **B** | RCDO hierarchy (model + read API + seed) | Platform foundation | Light — mostly data model | A |
| **C** | Weekly lifecycle engine (backend) | Weekly lifecycle | ✅ Done (brainstorm exists) | A, B |
| **D** | Weekly lifecycle frontend (IC screens) | Weekly lifecycle | Recommended | A, C |
| **E** | Chess layer (categorization + prioritization) | Weekly lifecycle | ✅ Yes — underspecified | C, D |
| **F** | Manager dashboard + team roll-up | Manager visibility | ✅ Yes — underspecified | A, C |
| **G** | E2E, performance hardening & deliverables | Platform foundation | No | A–F |

---

## Requirement-by-requirement traceability

Every PRD line item, assigned to at least one workstream.

### Functional requirements

| PRD requirement | Workstream(s) |
|---|---|
| Weekly commit CRUD with RCDO hierarchy linking | C (CRUD + link), B (link target) |
| Chess layer for categorization and prioritization | E |
| Full weekly lifecycle state machine (DRAFT→LOCKED→RECONCILING→RECONCILED→Carry Forward) | C |
| Reconciliation view comparing planned vs. actual | C (data), D (view) |
| Manager dashboard with team roll-up | F |
| Micro-frontend integration into PA host (PM remote pattern) | A (remote skeleton), D/E/F (each remote screen) |

### Performance benchmarks

| PRD requirement | Workstream(s) |
|---|---|
| API < 200ms for plan retrieval | C |
| Lazy-loaded routes for sub-second initial render | D, G |
| Module Federation remote bundle size optimized for CDN | A, G |
| Pagination (Spring Data Pageable) up to 2000 records | F |

### Code quality

| PRD requirement | Workstream(s) |
|---|---|
| TypeScript strict mode | A (config), all FE plans |
| JaCoCo ≥ 80% backend coverage | A (CI gate), C/B/F backends |
| Vitest unit tests for all components | A (setup), all FE plans |
| Cypress E2E with Cucumber/Gherkin BDD | A (setup), G (suite) |
| ESLint 9 + Prettier 3.3 (frontend) | A |
| Spotless + SpotBugs (backend) | A |
| All entities extend `AbstractAuditingEntity` | A (base entity), all backend plans |
| RTK Query for all API calls + cache invalidation | A (setup), all FE plans |

### Technology constraints (apply to all workstreams)

- Required: Java 21, Spring Boot 3.3, Spring Data JPA + Hibernate, PostgreSQL 16.4, Flyway, Auth0 (OAuth2 JWT), Lombok `@Getter/@Setter/@Builder`; TypeScript strict, React 18, Vite 5 Module Federation, RTK Query, Flowbite React, Tailwind, Vitest, Playwright.
- Off-limits: CSS Modules / styled-components; Redux Saga / Thunk; Prisma / TypeORM / Sequelize; Lombok `@Data`; SSR frameworks (Next.js / Remix).
- Listed but **not required to replicate** for this standalone assessment: AWS infra (EKS, CloudFront, S3, SQS/SNS), LogRocket/Loki monitoring, Nx/Yarn-workspaces tooling, Outlook Graph integration.

### Submission deliverables

| PRD deliverable | Workstream(s) |
|---|---|
| Source Code | A–F |
| Technical Documentation | G |
| Demo Video | G |
| Test Results | G |
| AI Usage Log | Ongoing (maintained across all work) |

---

## Suggested sequence (1-week build)

Critical path: **A → B → C → D**, with **E** and **F** following once the lifecycle exists,
and **G** wrapping up cross-cutting concerns and deliverables.

```
A  Foundation ──┬─> B  RCDO ──> C  Lifecycle backend ──> D  Lifecycle FE ──┬─> E  Chess layer
                │                          │                               │
                │                          └────────────> F  Manager dash ─┤
                └───────────────────────────────────────────────────────> G  E2E / perf / deliverables
```

- **A and B** are prerequisites for everything; do them first.
- **C** is fully brainstormed and ready to plan now.
- **E and F** each need their own `ce-brainstorm` — they're underspecified today.
- **G** is continuous-but-finalized-last (tests and docs accrue throughout; perf hardening and the demo video come at the end).

---

## Next actions

- **C (lifecycle backend)** — brainstorm done; ready for `ce-plan` immediately.
- **A (foundation)** — could be planned now; it's the true first build step.
- **B (RCDO)** — quick brainstorm or a light plan; needed before C can link commits.
- **E (chess layer)**, **F (manager dashboard)** — run `ce-brainstorm` before planning.

---

## Sources

- PRD: [PRD-ST6.md](../PRD-ST6.md)
- Strategy: [STRATEGY.md](../STRATEGY.md)
- Lifecycle brainstorm: [weekly-lifecycle-state-machine-requirements.md](brainstorms/weekly-lifecycle-state-machine-requirements.md)
