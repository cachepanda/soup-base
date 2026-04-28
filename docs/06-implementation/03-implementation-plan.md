# Soup-Base — Implementation Plan

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>
**Team:** 1 engineer + AI coding agents<br>
**Parent documents:** [PRD v1.0](../01-product/01-prd.md), [Functional Requirements v1.0](../02-requirements/01-functional-requirements.md), [ADRs v1.0](../03-architecture/01-adrs.md), [Data Model v1.0](../03-architecture/05-data-model.md), [Module Boundaries v1.0](./02-module-boundaries.md), [Tech Stack v1.0](./01-tech-stack.md)<br>

---

## Approach

One engineer reviews and merges. AI agents implement. Tasks are ordered so each phase
delivers something runnable end-to-end — not a horizontal layer cake.

**Definition of done per task:**
1. Code compiles and CI passes.
2. Integration tests cover the acceptance criteria from the FR.
3. The feature works end-to-end (API or UI).

---

## Phase 0 — Scaffolding

**Goal:** A running skeleton. No business logic. Backend starts, connects to both databases,
serves a health check. Frontend loads. CI passes.

**AI agent suitability:** Very high — pure boilerplate.

| Done | # | Task | Output | FR |
|---|---|---|---|---|
| ⬜ | 0.1 | Gradle project: single module, Kotlin DSL, Spring Boot 3.4, jOOQ 3.19, Flyway 10, Testcontainers. Java 21 with virtual threads enabled. | `backend/build.gradle.kts`, `settings.gradle.kts` | — |
| ⬜ | 0.2 | Spring Boot app entry point. `application.yml` with datasource config for control-plane DB. Actuator health endpoint at `/actuator/health`. | `SoupBaseApplication.java`, `application.yml` | NFR-OPS-002 |
| ⬜ | 0.3 | Docker Compose: control-plane Postgres (port 5432), hosted-cluster Postgres (port 5433), PgBouncer (port 6432) pointing at hosted cluster. | `infra/docker/docker-compose.yml` | — |
| ⬜ | 0.4 | Flyway migration V1: create `users` and `databases` tables and `database_status` enum per data model doc. Run against docker-compose Postgres. | `backend/src/main/resources/db/migration/V1__init.sql` | — |
| ⬜ | 0.5 | jOOQ codegen: generate type-safe classes from the migrated schema. Verify compilation. | `backend/src/main/generated/` (gitignored, generated at build time) | — |
| ⬜ | 0.6 | `ClerkJwtFilter`: validates `Authorization: Bearer <token>` on every request. Extracts `clerk_id` and sets a `ClerkPrincipal` in the security context. Public endpoints (`/actuator/health`) bypass the filter. | `ClerkJwtFilter.java`, `SecurityConfig.java` | NFR-SEC-001, NFR-SEC-003 |
| ⬜ | 0.7 | React frontend scaffold: Vite + React 18 + TypeScript + Tailwind + TanStack Query + Clerk provider. Blank dashboard page behind Clerk sign-in. | `frontend/` | — |
| ⬜ | 0.8 | Backend CI workflow: checkout, Java 21, Gradle compile + Flyway migrate + jOOQ codegen + `./gradlew test`. Runs on every PR and push to master. | `.github/workflows/backend.yml` | — |
| ⬜ | 0.9 | Frontend CI workflow: `npm ci`, `npm run lint`, `npm run build`. Runs on every PR touching `frontend/`. | `.github/workflows/frontend.yml` | — |

**Exit criteria:** `docker compose up` starts all three containers. `./gradlew bootRun`
starts the backend and `/actuator/health` returns `UP`. `npm run dev` loads the frontend
with a Clerk sign-in screen. CI passes on an empty commit.

---

## Phase 1 — Core Backend

**Goal:** All database management API endpoints work. A developer can use `curl` or
Postman to provision a database, retrieve its connection string, and delete it.

**AI agent suitability:** High — well-specified CRUD with clear acceptance criteria.

| Done | # | Task | Output | FR |
|---|---|---|---|---|
| ⬜ | 1.1 | `UserService` + `UserRepository`: find-or-create user by `clerk_id` on first sign-in. `POST /api/auth/session` endpoint. | `UserService`, `UserRepository`, `AuthController` | FR-AU-002 |
| ⬜ | 1.2 | `HostedClusterClient`: executes `CREATE DATABASE`, `CREATE ROLE`, `GRANT`, `DROP DATABASE`, `DROP ROLE` on the hosted-cluster Postgres. Uses a dedicated admin JDBC connection (separate from the control-plane datasource). | `HostedClusterClient.java` | ADR-001 |
| ⬜ | 1.3 | `DatabaseService` + `DatabaseRepository`: create database record, enforce 3-database limit, enforce unique name per user. | `DatabaseService`, `DatabaseRepository` | FR-DB-001 |
| ⬜ | 1.4 | Async provisioning: `ProvisioningService` runs in a `@Async` thread. Creates Postgres DATABASE + ROLE on hosted cluster, updates record to `ACTIVE` or `FAILED`. Startup recovery job handles stuck `PROVISIONING` records older than 60 seconds. | `ProvisioningService`, `RecoveryJob` | FR-DB-001, NFR-REL-002, NFR-REL-003 |
| ⬜ | 1.5 | `POST /api/databases` — create database. Returns `202` with `PROVISIONING` record. | `DatabaseController` | FR-DB-001 |
| ⬜ | 1.6 | `GET /api/databases` — list user's databases (no credentials). | `DatabaseController` | FR-DB-003 |
| ⬜ | 1.7 | `GET /api/databases/{id}` — get single database with credentials when `ACTIVE`. Owner-only (404 for non-owner). | `DatabaseController` | FR-DB-002 |
| ⬜ | 1.8 | `GET /api/databases/{id}/status` — lightweight status poll. | `DatabaseController` | FR-DB-005 |
| ⬜ | 1.9 | `DELETE /api/databases/{id}` — async deletion. Transitions to `DELETING`, drops DATABASE + ROLE on hosted cluster, transitions to `DELETED`. | `DatabaseController`, `ProvisioningService` | FR-DB-004 |
| ⬜ | 1.10 | `POST /api/databases/{id}/rotate-credentials` — generate new password, update Postgres role, return new credentials. | `DatabaseController`, `DatabaseService` | FR-DB-006 |
| ⬜ | 1.11 | Integration tests for all endpoints: happy path, 401 unauthenticated, 404 non-owner, limit enforcement, status transitions. Uses Testcontainers for both control-plane and hosted-cluster Postgres. | `DatabaseControllerApiTest` | All FR-DB |

**Exit criteria:** All 11 FR-DB acceptance criteria pass via API test. A real connection
string returned from `GET /api/databases/{id}` successfully connects to the hosted-cluster
Postgres from a standard client (`psql`).

---

## Phase 2 — Frontend

**Goal:** A user can sign up, create a database, copy the connection string, and delete a
database entirely through the UI.

**AI agent suitability:** High — components are well-specified in FR-UI.

| Done | # | Task | Output | FR |
|---|---|---|---|---|
| ⬜ | 2.1 | Dashboard page: list databases with status badges. "New Database" button disabled when limit reached. Polls status every 2s for transitional databases. | `DashboardPage`, `DatabaseCard`, `StatusBadge`, `useDatabases` hook | FR-UI-001 |
| ⬜ | 2.2 | New database modal: name field with validation, submit triggers `POST /api/databases`, shows new card in provisioning state immediately. | `CreateDatabaseModal` | FR-UI-003 |
| ⬜ | 2.3 | Database detail page: connection string with copy button, masked password with reveal toggle, individual credential fields. | `DatabaseDetailPage`, `ConnectionStringDisplay` | FR-UI-002 |
| ⬜ | 2.4 | Rotate credentials button with confirmation dialog. Delete database button with confirmation dialog. | `DatabaseDetailPage` | FR-UI-002 |

**Exit criteria:** Full user journey works in the browser: sign up → create database →
wait for active → copy connection string → connect with psql → delete database.

---

## Phase 3 — Infrastructure & Deploy

**Goal:** The app runs on AWS. A staging URL exists. Production deploy requires manual
approval.

**AI agent suitability:** Medium — CDK is well-defined but infra changes carry risk.
Human reviews all CDK PRs before merge.

| Done | # | Task | Output | FR |
|---|---|---|---|---|
| ⬜ | 3.1 | CDK stacks: VPC, ECS Fargate service, RDS (control-plane), RDS (hosted cluster), Secrets Manager entries for DB credentials. | `infra/cdk/` | ADR-006 |
| ⬜ | 3.2 | PgBouncer ECS task: sidecar to the app, reads hosted-cluster credentials from Secrets Manager, config templated per-database on reload. | PgBouncer ECS task definition | ADR-002 |
| ⬜ | 3.3 | Backend deploy workflow: build Docker image → push to ECR → deploy to ECS staging on merge to master. Production deploy on manual approval. | `.github/workflows/deploy-backend.yml` | — |
| ⬜ | 3.4 | Frontend deploy workflow: `npm run build` → deploy to Firebase Hosting staging channel on PR, production on merge to master. | `.github/workflows/deploy-frontend.yml` | — |

**Exit criteria:** `https://staging.soup-base.dev` serves the app. Full user journey works
on staging against real AWS infrastructure.
