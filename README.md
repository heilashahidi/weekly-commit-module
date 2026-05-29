# weekly-commit-module

Weekly Commit (WC) — a micro-frontend module that links individual weekly commitments to the
organizational RCDO strategy hierarchy (Rally Cries → Defining Objectives → Outcomes → Supporting
Outcomes), enforcing strategic alignment through a structured weekly lifecycle.

See [`STRATEGY.md`](STRATEGY.md) for product direction, [`PRD-ST6.md`](PRD-ST6.md) for the full
spec, and [`docs/prd-coverage-roadmap.md`](docs/prd-coverage-roadmap.md) for how the work is
decomposed into plans.

## Repository layout

```
weekly-commit-module/
├── backend/    # Spring Boot 3.3 / Java 21 API (Gradle, JPA/Hibernate, PostgreSQL, Flyway, Auth0)
├── frontend/   # React 18 + Vite 5 Module Federation remote (RTK Query, Tailwind + Flowbite)
└── docs/       # Strategy, brainstorms, plans, roadmap
```

This is a standalone monorepo (plain `backend/` + `frontend/` folders — no Nx/Yarn-workspaces).
In production the frontend is consumed as a Vite Module Federation remote by the PA host app, but
it runs fully standalone for local development and this assessment.

## Running locally

### Backend

```bash
cd backend
./gradlew bootRun        # starts the API (requires a local PostgreSQL 16.x)
./gradlew check          # format, static analysis, @Data ban, line-coverage gate
```

Configuration is supplied via environment variables (see `backend/src/main/resources/application.yml`).
For local development without an Auth0 tenant, run under the `dev` profile (auth relaxed):

```bash
SPRING_PROFILES_ACTIVE=dev WC_LOCAL_DEV=true ./gradlew bootRun
```

### Frontend

```bash
cd frontend
npm install
npm run dev              # standalone dev server
npm run build && npm run preview   # build + preview (serves the Module Federation remoteEntry)
npm run test             # Vitest unit tests
npm run e2e              # Cypress (Cucumber/Gherkin) E2E
```

## Tech stack

Java 21 · Spring Boot 3.3 · Spring Data JPA / Hibernate · PostgreSQL 16.4 · Flyway · Auth0 (OAuth2
JWT) · TypeScript (strict) · React 18 · Vite 5 (Module Federation) · Redux Toolkit + RTK Query ·
Tailwind CSS + Flowbite React · Vitest · Cypress (Cucumber/Gherkin).
