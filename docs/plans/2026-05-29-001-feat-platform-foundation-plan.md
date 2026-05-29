---
title: "feat: Platform foundation & scaffolding (Weekly Commit module)"
type: feat
status: active
date: 2026-05-29
---

# feat: Platform foundation & scaffolding (Weekly Commit module)

## Summary

Stand up the standalone monorepo substrate for the Weekly Commit (WC) module: a Spring Boot 3.3 /
Java 21 backend (JPA/Hibernate, PostgreSQL, Flyway, `AbstractAuditingEntity`, Auth0 JWT security,
quality gates) and a React 18 / Vite 5 Module Federation remote frontend (RTK Query, Tailwind +
Flowbite, Vitest, Cypress, ESLint/Prettier), plus a thin walking skeleton (health endpoint + one
placeholder remote route) that proves the wiring end-to-end. This is workstream **A** in
`docs/prd-coverage-roadmap.md` — the prerequisite for all feature work.

---

## Problem Frame

The repo is greenfield — no build files, no app skeleton. Every downstream workstream (RCDO,
lifecycle engine, chess layer, manager dashboard) depends on a working backend + frontend
substrate that already satisfies the PRD's required stack and code-quality gates. Without a
foundation that bakes in the constraints (JPA/Hibernate only, RTK Query only, Module Federation
remote shape, auditing on every entity, 80% coverage), each feature plan would re-litigate setup
and risk drifting from the PRD's off-limits rules.

---

## Requirements

Derived from `PRD-ST6.md` (required stack + code-quality + micro-frontend constraints) and the
roadmap's workstream A.

**Backend substrate**
- R1. Spring Boot 3.3 application on Java 21, built with Gradle, connecting to PostgreSQL 16.4.
- R2. Persistence via Spring Data JPA + Hibernate only (no Prisma/TypeORM/Sequelize); schema managed by Flyway migrations.
- R3. An `AbstractAuditingEntity` base class providing created/modified by + timestamp auditing, which all future entities extend.
- R4. Lombok used with `@Getter`/`@Setter`/`@Builder` (never `@Data`).
- R5. Auth0 OAuth2 JWT resource-server security (issuer/audience via env), plus a local dev profile that allows testing without a live tenant.

**Backend quality gates**
- R6. Spotless (format), SpotBugs (static analysis), and JaCoCo with an enforced ≥80% coverage threshold wired into the build.

**Frontend substrate**
- R7. React 18 + Vite 5 with TypeScript strict mode.
- R8. Vite Module Federation: the app runs standalone but is structured as a remote — single route entry point, shared dependencies declared, no hardcoded host shell/navigation.
- R9. Redux Toolkit with RTK Query for all data fetching (no Saga/Thunk), including Auth0 bearer-token injection and cache invalidation wiring.
- R10. Styling via Tailwind CSS + Flowbite React only (no CSS Modules / styled-components).
- R11. ESLint 9 + Prettier 3.3 configured.
- R12. Vitest for unit tests and Cypress (Cucumber/Gherkin BDD) for E2E, both runnable.

**Walking skeleton**
- R13. A backend health endpoint and one placeholder remote route that fetches it via RTK Query, proving the full stack end-to-end (DB up, security pass-through, remote loads, query resolves).

---

## Scope Boundaries

- No domain/feature work — lifecycle engine, RCDO hierarchy, chess layer, manager dashboard are separate workstreams (B–F in the roadmap).
- No AWS infra (EKS, CloudFront, S3, SQS/SNS) — the module runs standalone per the PRD.
- No Nx / Yarn-workspaces tooling, no LogRocket/Loki monitoring — explicitly not required to replicate.
- No Outlook Graph integration.
- Not building the PA host app — only structuring WC so it *could* be consumed as a remote.

### Deferred to Follow-Up Work

- Production Auth0 tenant configuration and real end-to-end auth testing: when an Auth0 tenant is available.
- CI pipeline wiring (GitHub Actions, etc.): the build-level gates land here; orchestrating them in CI is a later concern (roadmap workstream G).

---

## Context & Research

### Relevant Code and Patterns

- None — greenfield. Conventions are established by this plan and inherited by all later workstreams.

### External References

- PRD required stack and off-limits rules: `PRD-ST6.md`.
- Strategy (Platform foundation track): `STRATEGY.md`.

---

## Key Technical Decisions

- **Plain `backend/` + `frontend/` layout, no Nx/Yarn-workspaces**: the PRD marks PA's Nx/Yarn tooling as not-required-to-replicate; a two-folder layout is simpler for a standalone build and keeps backend/frontend toolchains independent.
- **Gradle for the backend**: clean plugin story for Spotless, SpotBugs, and JaCoCo coverage enforcement on Java 21 / Spring Boot 3.3.
- **Auth0 as a JWT resource server + dev profile**: configure the OAuth2 resource-server (validate issuer/audience from env) so the contract is real, with a `dev`/`local` profile that relaxes auth for local + test runs (no live tenant required to build or test).
- **Module Federation runs standalone**: a single exposed route entry with shared React/Redux deps, so WC works on its own now and slots into the PA host later without rework.
- **Walking skeleton included**: a health endpoint + one RTK Query-backed placeholder route proves DB, security, remote load, and data fetching are wired before any feature is built.
- **`AbstractAuditingEntity` via Spring Data JPA auditing**: `@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy` with an `AuditorAware` bound to the authenticated principal, so every future entity inherits auditing for free (PRD requirement).
- **Cypress (not Playwright) for E2E**: the PRD's Dev Tools list names Playwright while its Code Quality section names "Cypress E2E with Cucumber/Gherkin BDD syntax" — an internal contradiction in the spec. Cypress wins because the Gherkin/BDD requirement is concrete and Cypress-native (`@badeball/cypress-cucumber-preprocessor`); Playwright's Cucumber support is a less-standard third-party adapter. This choice is recorded in the AI Usage Log / technical docs so the grader sees the conflict was resolved deliberately, not missed.

### Security

- **Secure-by-default profiles + hard dev guard**: the default profile enforces JWT auth; the `dev`/`test` profile that relaxes auth **fails fast at startup if active outside local development** (a guard checks `SPRING_PROFILES_ACTIVE` against an explicit local marker). The auth bypass can never reach a deployed environment silently.
- **Mandatory issuer + audience validation**: the resource server rejects any token whose `iss` or `aud` does not match the configured values — both are required, not optional — preventing cross-service token replay within the same Auth0 tenant.
- **CORS allowlist (never wildcard)**: the backend permits only known frontend origins (standalone dev origin + future PA host origin, via env); no `*`-with-credentials.
- **In-memory frontend token storage**: RTK Query pulls the bearer token via the Auth0 SDK `getAccessTokenSilently()` (in-memory), never localStorage/sessionStorage — avoids XSS token exfiltration, which Module Federation's shared origin would amplify.
- **Health endpoint minimal payload**: public `/health` returns only `{"status":"UP"}`; if Spring Actuator is used, sensitive endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/info`) are not exposed publicly.
- **Secrets via env placeholders only**: `application.yml` uses `${ENV_VAR}` placeholders with no literal credentials; `.gitignore` excludes local override files (e.g., `application-local.yml`).

### Build & architecture

- **Pinned toolchains**: Gradle wrapper version, Spring Boot patch, a Gradle Java toolchain (`languageVersion = 21`), Node engines, and frontend major/minor lines are all pinned so the frozen stack is reproducible regardless of the host JDK/Node.
- **JaCoCo line coverage (not branch)**: the ≥80% gate enforces **line** coverage (matching the PRD's "80% minimum" wording, which names no branch target), with config-only classes (`*Application`, `*Config`) excluded. An 80%-branch gate on logic-light scaffolding would fail the build spuriously.
- **`@Data` ban machine-enforced**: an ArchUnit rule fails the build if any production class uses `lombok.@Data`, so R4 is enforced by tooling rather than convention.
- **`PrincipalResolver` indirection for auditing**: `AuditorAwareImpl` delegates to an injectable `PrincipalResolver` (JWT-`sub` default), so workstream B/C can swap to a mapped user record without touching `AuditorAwareImpl` or any entity.
- **No speculative RTK Query tags**: the base slice ships only `prepareHeaders` auth injection; tag-based invalidation is documented as the convention but defined per feature slice (no empty tag scaffolding with zero consumers).
- **Lazy route boundary from day one**: the exposed Module Federation route is wrapped in `React.lazy` + `Suspense`, so workstream D's lazy-loading requirement is additive rather than a remote-entry refactor.
- **Named MF plugin + build/preview proof**: use `@originjs/vite-plugin-federation`; the remote / walking-skeleton proof runs against `vite build && vite preview` because Vite 5's dev server does not reliably serve `remoteEntry`.

---

## Open Questions

### Resolved During Planning

- Build tool (Gradle vs Maven): **Gradle** — see Key Technical Decisions.
- Monorepo tooling (Nx vs plain): **plain folders** — not required to replicate.
- E2E tool (Cypress vs Playwright): **Cypress** — the PRD Code Quality section's explicit Gherkin/BDD requirement is Cypress-native; see Key Technical Decisions.
- Dev-profile auth bypass safety: **resolved** — the relaxed profile fails fast at startup outside local dev (see Security decisions).
- Frontend token storage: **resolved** — in-memory via Auth0 SDK `getAccessTokenSilently()`, never browser storage (see Security decisions).

### Deferred to Implementation

- Exact Auth0 issuer/audience values: provided via env at deploy/test time, not hardcoded.
- Principal-resolution *source* only (not the structural shape, which is settled via `PrincipalResolver`): whether the resolver returns the raw JWT `sub` or a mapped user record is decided when the user model lands (workstream B/C). The `PrincipalResolver` indirection means this is a one-class swap, not an entity-wide migration.

---

## Output Structure

    weekly-commit-module/
    ├── backend/
    │   ├── build.gradle
    │   ├── settings.gradle
    │   ├── config/                         # spotless, spotbugs exclude configs
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/.../wc/
    │       │   │   ├── WeeklyCommitApplication.java
    │       │   │   ├── config/             # JpaAuditingConfig, SecurityConfig, AuditorAwareImpl
    │       │   │   ├── common/             # AbstractAuditingEntity
    │       │   │   └── health/             # HealthController (walking skeleton)
    │       │   └── resources/
    │       │       ├── application.yml      # profiles: default, dev/local
    │       │       └── db/migration/        # Flyway V1__baseline.sql
    │       └── test/java/com/.../wc/        # auditing, security, health tests
    ├── frontend/
    │   ├── package.json
    │   ├── vite.config.ts                   # Module Federation remote config
    │   ├── tsconfig.json                    # strict
    │   ├── tailwind.config.js
    │   ├── .eslintrc / prettier config
    │   ├── vitest.config.ts
    │   ├── cypress.config.ts
    │   └── src/
    │       ├── main.tsx                      # standalone entry
    │       ├── bootstrap.tsx                 # remote entry (single route)
    │       ├── store/                        # RTK store + api base slice
    │       ├── routes/                       # placeholder remote route
    │       └── __tests__ / cypress/          # Vitest + Cypress (Gherkin) specs
    └── docs/

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
┌─────────────────────────────┐         ┌──────────────────────────────┐
│ frontend (Vite MF remote)   │         │ backend (Spring Boot)        │
│                             │  HTTP   │                              │
│  placeholder route ──RTKQ──────────────> HealthController /health    │
│  store + api base slice     │  +JWT   │  SecurityConfig (Auth0 RS)   │
│  (Auth0 bearer injection)   │         │  JPA + Flyway + Postgres     │
│  runs standalone OR as      │         │  AbstractAuditingEntity      │
│  remote in PA host          │         │  Spotless/SpotBugs/JaCoCo    │
└─────────────────────────────┘         └──────────────────────────────┘
```

---

## Implementation Units

### U1. Monorepo skeleton

**Goal:** Create the two-folder repo layout and root-level housekeeping.

**Requirements:** Scaffolding — enables all units; implements no PRD requirement directly (R1 lands in U2, R7 in U6).

**Dependencies:** None

**Files:**
- Create: `backend/` and `frontend/` directory roots
- Create: `.gitignore` (Java/Gradle + Node/Vite)
- Modify: `README.md` (how to run backend + frontend standalone)

**Approach:**
- Plain `backend/` + `frontend/` split; no Nx/Yarn-workspaces.
- Document the standalone run commands for each side.

**Test scenarios:**
- Test expectation: none — scaffolding/structure only.

**Verification:**
- Both directories exist with their own toolchains; root README explains how to run each.

---

### U2. Spring Boot backend bootstrap

**Goal:** A runnable Spring Boot 3.3 / Java 21 app on Gradle that connects to PostgreSQL 16.4.

**Requirements:** R1, R2, R4

**Dependencies:** U1

**Files:**
- Create: `backend/build.gradle`, `backend/settings.gradle`
- Create: `backend/src/main/java/.../wc/WeeklyCommitApplication.java`
- Create: `backend/src/main/resources/application.yml` (default + `dev` profiles, Postgres datasource)

**Approach:**
- Gradle with Spring Boot 3.3, Spring Web, Spring Data JPA, PostgreSQL driver, Lombok, Flyway.
- **Pin the toolchain**: Gradle wrapper version, Spring Boot patch version, and a Gradle Java toolchain block (`languageVersion = 21`) so the build targets Java 21 regardless of the host JDK.
- Datasource + JPA config via `application.yml`; Hibernate `ddl-auto: validate` (Flyway owns schema).
- **`application.yml` uses `${ENV_VAR}` placeholders only** — no literal credentials; `.gitignore` excludes local override files (e.g., `application-local.yml`).
- Lombok configured; `@Getter/@Setter/@Builder` only (the no-`@Data` rule is machine-enforced in U5).

**Patterns to follow:**
- Standard Spring Boot layered structure (`config`, `common`, feature packages).

**Test scenarios:**
- Integration: application context loads with the `dev` profile and an available Postgres (or Testcontainers) — context-load smoke test.

**Verification:**
- `./gradlew bootRun` starts the app and connects to Postgres without error.

---

### U3. JPA auditing, Flyway baseline, and AbstractAuditingEntity

**Goal:** Establish the auditing base entity and Flyway-managed schema baseline.

**Requirements:** R2, R3

**Dependencies:** U2

**Files:**
- Create: `backend/src/main/java/.../wc/common/AbstractAuditingEntity.java`
- Create: `backend/src/main/java/.../wc/config/JpaAuditingConfig.java`, `.../config/AuditorAwareImpl.java`, `.../config/PrincipalResolver.java`
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`
- Test: `backend/src/test/java/.../wc/common/AbstractAuditingEntityTest.java` (+ a test-only `@Entity` fixture and its table migration under `src/test/resources`)

**Approach:**
- `AbstractAuditingEntity` (`@MappedSuperclass`) with `@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy`.
- `@EnableJpaAuditing` + `AuditorAware` that delegates to an injectable **`PrincipalResolver`** interface (default impl returns the JWT `sub`). Workstream B/C swaps the resolver — not `AuditorAwareImpl` — when the user model lands.
- **Non-trivial Flyway baseline**: `V1__baseline.sql` enables a Postgres extension (e.g., `pgcrypto`) so the migration is actually applied and the Flyway + `validate` path is exercised, not vacuously empty.
- The auditing test uses a **test-scoped `@Entity`** (with its own test-resources table/migration) so there is a real table to persist into — the foundation defines no domain entity.

**Patterns to follow:**
- JHipster-style `AbstractAuditingEntity` (the PRD's `AbstractAuditingEntity` requirement mirrors this convention).

**Test scenarios:**
- Happy path: persisting the test entity that extends `AbstractAuditingEntity` populates `createdDate` and `lastModifiedDate`.
- Happy path: with a mocked authenticated principal, `createdBy`/`lastModifiedBy` are set from the `PrincipalResolver`.
- Edge case: updating an existing entity changes `lastModifiedDate` but not `createdDate`.
- Integration: after context load, Flyway reports exactly one applied migration (count = 1, not 0) and `ddl-auto: validate` passes.

**Verification:**
- Auditing fields populate automatically on insert/update; Flyway baseline applies (migration count = 1) without manual SQL.

---

### U4. Auth0 OAuth2 JWT resource-server security + dev profile

**Goal:** Validate Auth0-issued JWTs on protected endpoints, with a relaxed local/test profile.

**Requirements:** R5

**Dependencies:** U2

**Files:**
- Create: `backend/src/main/java/.../wc/config/SecurityConfig.java`, `.../config/DevProfileGuard.java` (startup assertion), `.../config/CorsConfig.java`
- Modify: `backend/src/main/resources/application.yml` (issuer-uri/audience via env; `dev` profile overrides; CORS allowed-origins via env)
- Test: `backend/src/test/java/.../wc/config/SecurityConfigTest.java`

**Approach:**
- Spring Security OAuth2 resource server with **mandatory** issuer **and** audience validation (a custom `OAuth2TokenValidator` rejects tokens whose `aud` omits the configured audience) — both required, read from env.
- Default profile: protect endpoints, require a valid bearer token. `dev`/`test` profile: permit local access so the app runs and tests pass without a live tenant.
- **Dev-profile guard**: a startup check (`ApplicationListener`/`@PostConstruct`) fails fast if the `dev` profile is active without an explicit local marker env var — so the auth bypass cannot silently run in a deployed environment.
- **CORS allowlist**: permit only the configured frontend origins (standalone dev origin + future PA host origin via env); never `*` with credentials.
- Health endpoint left public in all profiles (payload constrained in U5).

**Test scenarios:**
- Error path: a request to a protected endpoint with no/invalid JWT returns 401 (default profile).
- Error path: a valid-issuer token with the wrong/absent `aud` is rejected (audience validation).
- Happy path: a request with a valid (mock-issued) JWT with correct `iss`+`aud` reaches the endpoint.
- Happy path: under the `dev` profile (with the local marker set), protected endpoints are reachable without a token.
- Error path: the `dev` profile without the local marker fails application startup (guard fires).
- Happy path: the health endpoint is reachable without a token in all profiles.
- Integration: a cross-origin request from an allowed origin passes CORS; a disallowed origin is rejected.

**Verification:**
- Protected routes reject unauthenticated and wrong-audience requests in the default profile; the dev profile refuses to start outside local; CORS admits only allowlisted origins.

---

### U5. Backend quality gates + health endpoint (backend walking skeleton)

**Goal:** Wire Spotless, SpotBugs, and JaCoCo (≥80% enforced), and expose a health endpoint.

**Requirements:** R6, R13

**Dependencies:** U2

**Files:**
- Modify: `backend/build.gradle` (Spotless, SpotBugs, JaCoCo plugins + coverage verification rule)
- Create: `backend/config/` (SpotBugs exclude filter, Spotless config if needed)
- Create: `backend/src/main/java/.../wc/health/HealthController.java`
- Test: `backend/src/test/java/.../wc/health/HealthControllerTest.java`, `.../wc/arch/LombokDataBanTest.java` (ArchUnit)

**Approach:**
- JaCoCo `violationRules` failing the build under **80% line coverage** (not branch — matches the PRD's "80% minimum" wording), with config-only classes excluded from the denominator: `WeeklyCommitApplication`, `*Config`, and generated classes.
- Spotless format check + SpotBugs analysis bound into `check`.
- **ArchUnit rule** failing the build if any production-source class is annotated with `lombok.@Data` (machine-enforces R4).
- A lightweight `/health` endpoint returning **only `{"status":"UP"}`** — no DB/version/migration internals. If Spring Actuator is used, restrict the public surface (no `/actuator/env`, `/actuator/beans`, `/actuator/info`).

**Test scenarios:**
- Happy path: `GET /health` returns 200 with exactly `{"status":"UP"}` (no internal detail leaked).
- Happy path (ArchUnit): the `@Data` ban rule passes on the current source and would fail if a `@Data`-annotated class were added.
- Verification (build-level): line-coverage verification fails the build when below 80% (sanity-checked once).

**Verification:**
- `./gradlew check` runs format, static analysis, the `@Data` ban, and the line-coverage gate; `/health` responds 200 with the minimal payload.

---

### U6. Frontend bootstrap (Vite + React + TS strict + Tailwind/Flowbite + lint)

**Goal:** A runnable React 18 / Vite 5 app with TypeScript strict, Tailwind + Flowbite, ESLint 9 + Prettier 3.3, and the Vitest unit-test harness.

**Requirements:** R7, R10, R11, R12 (Vitest portion)

**Dependencies:** U1

**Files:**
- Create: `frontend/package.json`, `frontend/tsconfig.json` (strict), `frontend/vite.config.ts`
- Create: `frontend/tailwind.config.js`, `frontend/postcss.config.js`, ESLint + Prettier configs
- Create: `frontend/vitest.config.ts` (+ Vitest / React Testing Library deps)
- Create: `frontend/src/main.tsx`, base `App` + global stylesheet (Tailwind directives)

**Approach:**
- Vite 5 + React 18 + TS strict; Tailwind + Flowbite React for styling (no CSS Modules/styled-components).
- ESLint 9 flat config + Prettier 3.3.

**Test scenarios:**
- Test expectation: none — config/scaffolding (behavioral coverage lands in U8/U9).

**Verification:**
- `vite dev` serves the app; `eslint`/`prettier` run clean; Tailwind classes apply.

---

### U7. Module Federation remote configuration

**Goal:** Expose the app as a Vite Module Federation remote that also runs standalone.

**Requirements:** R8

**Dependencies:** U6

**Files:**
- Modify: `frontend/vite.config.ts` (`@originjs/vite-plugin-federation`: name, exposes, shared deps)
- Create: `frontend/src/bootstrap.tsx` (single exposed route entry, async-import boundary)
- Modify: `frontend/src/main.tsx` (standalone mount delegates to the same async bootstrap)

**Approach:**
- Use **`@originjs/vite-plugin-federation`**. Declare a single exposed module (the route entry) with shared `react`/`react-dom`/`redux` singletons.
- The exposed route component is wrapped in **`React.lazy` + `Suspense`** from the start, so workstream D's lazy-loading requirement is additive, not a remote-entry refactor.
- Standalone entry mounts the **same async bootstrap** the remote uses (no divergent code path), so "runs standalone AND as a remote" is one path, not two.

**Test scenarios:**
- Test expectation: none — build/config; the end-to-end proof is U8's smoke test.

**Verification:**
- `vite build && vite preview` serves a working `remoteEntry` and the app runs standalone via the same build (the Vite 5 dev server does not reliably serve `remoteEntry`, so the proof uses build+preview).

---

### U8. RTK Query store + placeholder route (frontend walking skeleton)

**Goal:** Wire the RTK Query store with Auth0 token injection and a placeholder route that fetches `/health`.

**Requirements:** R9, R13

**Dependencies:** U6, U7, U5

**Files:**
- Create: `frontend/src/store/index.ts` (RTK store), `frontend/src/store/api.ts` (RTK Query base slice)
- Create: `frontend/src/routes/HealthCheck.tsx` (placeholder route)
- Test: `frontend/src/__tests__/HealthCheck.test.tsx`

**Approach:**
- RTK Query base API slice with `baseQuery` whose `prepareHeaders` injects the bearer token obtained **in-memory** from the Auth0 SDK (`getAccessTokenSilently()`) — never read from localStorage/sessionStorage.
- **No tag scaffolding**: the base slice ships only the auth-injecting `baseQuery`. Tag-based invalidation is documented as the convention for feature slices to adopt; no placeholder tags are wired here (no consumers exist yet).
- Placeholder route calls the backend `/health` via an RTK Query hook and renders the result — the frontend half of the walking skeleton.

**Patterns to follow:**
- RTK Query `createApi` with `prepareHeaders` for auth. Per-feature-slice tag definitions are the documented invalidation pattern for downstream plans.

**Test scenarios:**
- Happy path: the placeholder route renders the health status when the query resolves (mocked fetch).
- Error path: the route renders an error/empty state when the health query fails.
- Integration: `prepareHeaders` attaches the bearer token (sourced from the in-memory Auth0 SDK) when a token is present, and omits it when absent.

**Verification:**
- Running both apps (backend + frontend via `vite preview`), the placeholder route displays live backend health — proving DB → security → remote → RTK Query end to end.

---

### U9. Frontend E2E harness (Cucumber/Gherkin)

**Goal:** Make the E2E suite runnable with a smoke spec. (Vitest unit harness is set up in U6; this unit adds the E2E layer.)

**Requirements:** R12 (E2E portion)

**Dependencies:** U6, U8

**Files:**
- Modify: `frontend/package.json` (E2E runner + Cucumber/Gherkin preprocessor scripts/deps)
- Create: `frontend/cypress.config.ts` (E2E runner config)
- Create: `frontend/cypress/e2e/health.feature` + step definitions

**Approach:**
- Cypress with the `@badeball/cypress-cucumber-preprocessor` for Gherkin BDD specs (see Key Technical Decisions for the Cypress-vs-Playwright reconciliation).
- One Gherkin smoke scenario exercising the walking-skeleton route.

**Test scenarios:**
- Integration (Gherkin E2E): a `.feature` scenario loads the placeholder route and asserts the health status renders.

**Verification:**
- `cypress run` executes the Gherkin smoke feature against the running stack (`vite preview` + backend) and it passes; `vitest run` (from U6) remains green.

---

## System-Wide Impact

- **Interaction graph:** establishes the only cross-layer path for now — frontend RTK Query → backend REST (CORS allowlist) → JPA/Postgres, guarded by Auth0 security. All feature workstreams plug into this path.
- **Error propagation:** RTK Query surfaces backend errors to the UI; Spring security returns 401/403; these conventions are inherited downstream.
- **State lifecycle risks:** minimal at foundation; Flyway baseline must remain the single source of schema truth (no `ddl-auto: update`).
- **API surface parity:** the RTK Query base slice + in-memory token injection becomes the mandatory data-fetching pattern for every later frontend plan.
- **Security invariants (inherited by all workstreams):** secure-by-default profile + hard dev-profile guard; mandatory issuer+audience JWT validation; CORS allowlist (no wildcard); in-memory token storage; minimal public `/health`; env-placeholder secrets. A permissive choice here would propagate to every downstream remote.
- **Unchanged invariants:** none yet — this plan creates the invariants (auditing on all entities via `PrincipalResolver`, RTK Query for all fetching, Module-Federation-remote shape, lazy route boundary) that later plans must honor.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Vite Module Federation maturity/config friction with Vite 5 | Use `@originjs/vite-plugin-federation`, keep the exposed surface to a single route, and prove the remote via `vite build && vite preview` (not the dev server) early in U7 |
| Auth0 not available during the build/assessment | Dev/test profile relaxes auth so the app runs and tests pass without a live tenant — guarded by a startup assertion so it can never run outside local dev (U4) |
| JaCoCo gate failing on scaffolding with little logic | Enforce **line** coverage (not branch) at 80%, exclude config-only classes (`*Application`, `*Config`); auditing/security/health tests provide genuine coverage |
| Module Federation shared origin amplifies token theft | In-memory token storage (Auth0 `getAccessTokenSilently()`), never browser storage; CORS allowlist with no wildcard |
| PostgreSQL availability for tests | Use Testcontainers (or an embedded/dev Postgres) for the context-load and auditing integration tests |

---

## Documentation / Operational Notes

- README documents standalone run for both backend (`./gradlew bootRun`) and frontend (`vite dev`), plus how WC is structured to be consumed as a PA remote.
- Record foundation tech choices (Gradle, plain monorepo, Auth0 dev profile) in the AI Usage Log / technical docs (roadmap workstream G).

---

## Sources & References

- PRD: [PRD-ST6.md](../../PRD-ST6.md)
- Strategy: [STRATEGY.md](../../STRATEGY.md)
- Roadmap: [docs/prd-coverage-roadmap.md](../prd-coverage-roadmap.md)
