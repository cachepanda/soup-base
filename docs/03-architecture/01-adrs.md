# Soup-Base — Architecture Decision Records

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## ADR-001 — Database isolation: one Postgres DATABASE per user project

**Status:** Accepted

**Context:**
Users need their data isolated from other users. Options considered:
- Schema per project on a shared database
- Separate Postgres DATABASE per project on a shared RDS instance
- Separate RDS instance per project

**Decision:**
One Postgres DATABASE per user project on a shared RDS instance, with a dedicated Postgres
role per database. The role has `CONNECT` and full privileges only on its own database.

**Rationale:**
- Schema isolation is weaker — a superuser or misconfigured role can cross schema boundaries.
- Separate RDS instances are expensive ($15+/month per instance) and slow to provision (minutes).
- Separate databases on a shared instance give strong isolation at the Postgres level with
  fast provisioning (< 1 second to `CREATE DATABASE` and `CREATE ROLE`).
- PgBouncer can be configured per-database, enforcing per-database connection limits.

**Consequences:**
- The shared RDS instance is a single point of failure for all hosted databases. Acceptable
  for v1 scope.
- Database names on the shared cluster follow the pattern `sb_{userId_hash}_{projectId}` to
  avoid collisions and keep names internal (not user-visible).
- Maximum databases on a single Postgres instance is ~10,000 — well beyond v1 needs.

---

## ADR-002 — Connection pooling via PgBouncer

**Status:** Accepted

**Context:**
Postgres has a hard connection limit per instance (~100 default on small RDS). With many
users connecting, each with their own apps, the shared cluster would hit connection limits
quickly.

**Decision:**
Run PgBouncer in front of the shared RDS cluster in transaction-pooling mode. Users connect
to PgBouncer, not directly to RDS. PgBouncer is configured with a 5-connection limit per
user database.

**Rationale:**
- Transaction-pooling multiplexes many client connections onto few server connections.
- Per-database limits in PgBouncer prevent a single user from starving others.
- PgBouncer is lightweight (single binary, < 50MB RAM) and battle-tested.

**Consequences:**
- Session-level features (prepared statements in session mode, advisory locks) are limited
  in transaction-pooling mode. Acceptable — users are warned in the docs.
- Connection strings point to PgBouncer's host/port, not RDS directly.
- PgBouncer config must be reloaded (via `SIGHUP`) when a new database is provisioned or
  deleted. The provisioning service handles this.

---

## ADR-003 — Auth delegated to Clerk

**Status:** Accepted

**Context:**
Building auth from scratch (password hashing, session management, email verification,
OAuth flows) is significant scope and a common source of security bugs.

**Decision:**
Use Clerk for all user authentication. The backend validates Clerk-issued JWTs.
The frontend uses Clerk's React SDK for sign-in/sign-up UI.

**Rationale:**
- Clerk handles OAuth (GitHub), email/password, email verification, and session management
  out of the box.
- JWT validation is stateless — the backend does not need a session store.
- Clerk's free tier covers the v1 user volume.

**Consequences:**
- Hard dependency on Clerk. If Clerk has an outage, users cannot sign in.
- No custom auth flows in v1 (SSO, SAML, magic links beyond what Clerk offers).
- The backend stores the Clerk `userId` as the owner FK for all resources.

---

## ADR-004 — Async provisioning with status polling

**Status:** Accepted

**Context:**
Provisioning a database involves creating a Postgres role and database on the hosted
cluster and reloading PgBouncer. This takes 1–3 seconds and should not block the HTTP
request.

**Decision:**
`POST /api/databases` returns `202 Accepted` immediately with `status: PROVISIONING`. A
background thread (Spring's `@Async`) completes the provisioning and updates the record
to `ACTIVE` or `FAILED`. The frontend polls `GET /api/databases/{id}/status` every 2
seconds until the status settles.

**Rationale:**
- Keeps the HTTP response fast regardless of hosted cluster load.
- Simple to implement — no message queue needed at v1 scale.
- Polling is sufficient for the expected UX (10-second window, infrequent operation).

**Consequences:**
- If the app process crashes mid-provisioning, the database stays `PROVISIONING` forever.
  A startup recovery job queries `PROVISIONING` records older than 60 seconds and attempts
  to complete or fail them.
- No message queue in v1. If provisioning volume grows, this is the first thing to replace
  with SQS + a worker.

---

## ADR-005 — Spring Boot backend, React frontend

**Status:** Accepted

**Context:**
Tech stack selection for the control plane.

**Decision:**
Spring Boot 3.x (Java 21) for the backend. React 18 + TypeScript + Vite for the frontend.

**Rationale:**
- Consistent with the FlowForge reference project — AI agents already have strong priors
  for this stack.
- Spring Boot's virtual threads (Project Loom) handle the async provisioning without
  explicit thread pool management.
- jOOQ provides type-safe SQL without ORM magic — easy to audit, easy to understand.

**Consequences:**
- Java 21 required. Build toolchain is Gradle.
- Frontend is a separate build artifact, deployed independently to Firebase Hosting.
- Backend and frontend are versioned independently.

---

## ADR-006 — Infrastructure on AWS with CDK

**Status:** Accepted

**Context:**
Where and how to run the control plane.

**Decision:**
AWS ECS Fargate for the backend. RDS PostgreSQL 16 for the control-plane database.
A separate RDS PostgreSQL 16 instance for the hosted databases. PgBouncer on ECS.
AWS CDK (TypeScript) for all infrastructure.

**Rationale:**
- ECS Fargate eliminates EC2 management.
- CDK allows infrastructure to be versioned, reviewed, and deployed by agents.
- Two separate RDS instances: one for soup-base's own data (control plane), one for user
  databases. This prevents a runaway user query from affecting soup-base's own operations.

**Consequences:**
- AWS account required. Initial CDK bootstrap is a one-time manual step.
- Cost: ~$150/month for a minimal production deployment (see deployment architecture doc).
