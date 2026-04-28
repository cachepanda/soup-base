# AGENTS.md — Soup-Base

Canonical session preamble for all agents (Codex, Claude Code, and any future agents).
Read this file first. Read deeper docs only when the task requires them.

---

## 1. Project Overview

**Soup-Base** is a lightweight hosted Postgres service. Users sign up, create databases, and
receive connection strings they can use in their apps immediately. No clusters to configure,
no infra to manage.

This is an AI-driven development practice project. The codebase is written primarily by
AI agents operating on specs in `docs/`. Humans write specs and review diffs. Agents write
code.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.x, Java 21, virtual threads |
| ORM / query | jOOQ (generated from schema), Flyway migrations |
| Frontend | React 18, TypeScript, Vite, TanStack Query, Tailwind CSS |
| Auth | Clerk (JWT validation on the backend, Clerk components on the frontend) |
| Control-plane DB | PostgreSQL 16 (soup-base's own metadata) |
| Hosted DB layer | PostgreSQL 16 shared RDS cluster — one Postgres DATABASE per user project |
| Connection pooling | PgBouncer (sits in front of the hosted cluster) |
| Infrastructure | AWS ECS Fargate, RDS, CDK (TypeScript) |
| CI | GitHub Actions |
| Container registry | Amazon ECR |

---

## 3. Repository Layout

```
soup-base/
├── AGENTS.md                  ← you are here
├── CLAUDE.md                  ← thin Claude wrapper
├── backend/                   ← Spring Boot application
│   ├── src/main/java/dev/soupbase/
│   │   ├── api/               ← REST controllers
│   │   ├── domain/            ← service layer + domain types
│   │   ├── db/                ← jOOQ repositories
│   │   └── infra/             ← AWS SDK calls (RDS provisioning)
│   ├── src/main/resources/
│   │   ├── db/migration/      ← Flyway changesets
│   │   └── application.yml
│   └── build.gradle.kts
├── frontend/                  ← React SPA
│   ├── src/
│   │   ├── pages/
│   │   ├── components/
│   │   ├── hooks/             ← TanStack Query hooks
│   │   └── api/               ← typed API client
│   ├── package.json
│   └── vite.config.ts
├── infra/
│   ├── cdk/                   ← CDK stacks
│   └── docker/
│       └── docker-compose.yml ← local dev
└── docs/                      ← specs (source of truth for agents)
```

---

## 4. Doc Load Order

Load in this order unless the task clearly needs something else:

1. `AGENTS.md` (this file)
2. `docs/01-product/01-prd.md`
3. `docs/02-requirements/01-functional-requirements.md`
4. `docs/03-architecture/01-adrs.md`
5. `docs/03-architecture/05-data-model.md`
6. `docs/06-implementation/02-module-boundaries.md`
7. `docs/06-implementation/04-testing-strategy.md`
8. `docs/06-implementation/05-coding-standards.md`

---

## 5. Build Commands

```bash
# Backend
cd backend
./gradlew compileJava          # compile + jOOQ codegen
./gradlew test                 # unit + architecture tests
./gradlew integrationTest      # integration tests (requires Docker)
./gradlew bootRun              # run locally

# Frontend
cd frontend
npm install
npm run dev                    # dev server on :5173
npm run build                  # production build
npm test                       # Vitest

# Local full stack
cd infra/docker
docker compose up              # Postgres (control plane) + Postgres (hosted cluster) + PgBouncer
```

---

## 6. Key Conventions

### Backend
- Package root: `dev.soupbase`
- Controllers in `api/`, services in `domain/`, repositories in `db/`
- All write endpoints return `Result<T>` (never throw checked exceptions across layer boundaries)
- Auth: Clerk JWT validated by `ClerkJwtFilter`. Every controller endpoint has `@PreAuthorize`
  or is annotated `@PublicEndpoint`.
- Database access: jOOQ only. No Spring Data JPA. No raw JDBC outside of Flyway migrations.
- Migrations: Flyway, files named `V{n}__{description}.sql` in `src/main/resources/db/migration/`
- Provisioning calls (create/delete RDS databases and roles): in `infra/` package only.
  Domain services call `ProvisioningService` — they never call AWS SDK directly.

### Frontend
- All server state through TanStack Query. No ad-hoc `fetch` calls in components.
- API client in `src/api/` — one file per resource. Typed request/response with Zod.
- Auth state from Clerk's `useAuth()` hook. Never store tokens manually.
- Forms: React Hook Form + Zod resolver.

### Testing
- Integration tests are the primary layer. Unit tests only for complex algorithms.
- No mocking of services or repositories in integration tests. Use Testcontainers.
- Every API endpoint has at least: happy-path test, unauthenticated test, not-found test.

---

## 7. Reviewer Checklist

Before opening a PR, verify:
- [ ] No business logic in controllers (controllers delegate to domain services)
- [ ] No AWS SDK calls outside `infra/` package
- [ ] Every controller endpoint has auth annotation or `@PublicEndpoint`
- [ ] Flyway migration included if schema changes
- [ ] jOOQ codegen re-run if migration added
- [ ] Integration test added for new endpoints
- [ ] No secrets or credentials in code or config files
