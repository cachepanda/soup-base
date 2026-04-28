# Soup-Base — Non-Functional Requirements

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>
**Parent:** [PRD v1.0](../01-product/01-prd.md)<br>

---

## Performance (NFR-PERF)

### NFR-PERF-001 — Provisioning latency
Database provisioning (from `POST /api/databases` to `status: ACTIVE`) must complete in
under 10 seconds under normal load.

### NFR-PERF-002 — API response time
Control-plane API endpoints (list, status, credentials) must respond within 300ms at p95
under normal load.

---

## Security (NFR-SEC)

### NFR-SEC-001 — Tenant isolation
A user must not be able to read, modify, or delete another user's database or credentials.
Every query to the control-plane database filters by the authenticated user's `clerk_id`.
The hosted Postgres cluster uses a dedicated role per database — no role can access another
user's database.

### NFR-SEC-002 — Credential storage
Database passwords are stored hashed (bcrypt) in the control-plane database. The plaintext
password is only available at creation time and after rotation. It is never logged.

### NFR-SEC-003 — Auth on every endpoint
Every API endpoint is either authenticated (Clerk JWT required) or explicitly marked as
public. No endpoint is accidentally unauthenticated.

### NFR-SEC-004 — No secrets in code or config
All secrets (RDS master password, Clerk secret key, hosted cluster credentials) are read
from environment variables or AWS Secrets Manager at runtime. They are never committed to
the repository.

### NFR-SEC-005 — Connection string security
Connection strings contain the database password. They must only be returned over HTTPS.
The backend enforces HTTPS-only in production. Connection strings must not appear in
server-side logs.

---

## Reliability (NFR-REL)

### NFR-REL-001 — Control plane availability
The control-plane API (creating, listing, deleting databases) targets 99.9% uptime.

### NFR-REL-002 — Provisioning failure handling
If provisioning fails (e.g., hosted cluster unreachable), the database transitions to
`status: FAILED`. The partial resources (database, role) are cleaned up. The user can
delete the failed record and retry.

### NFR-REL-003 — Idempotent provisioning
If the provisioning worker crashes mid-flight, a restart must not create duplicate
databases or roles on the hosted cluster. Provisioning operations check for existing
resources before creating them.

---

## Scalability (NFR-SCALE)

### NFR-SCALE-001 — User cap (v1)
v1 targets up to 500 registered users and 1,500 total active databases on a single
shared RDS instance. This is sufficient for a practice/MVP deployment.

### NFR-SCALE-002 — Connection limits
PgBouncer enforces a maximum of 5 concurrent client connections per database. This
protects the shared cluster from a single user exhausting the connection pool.

---

## Operability (NFR-OPS)

### NFR-OPS-001 — Structured logging
All backend logs are structured JSON. Every log line includes `timestamp`, `level`,
`userId` (where available), `databaseId` (where available), and `traceId`.
Passwords and connection strings are never logged.

### NFR-OPS-002 — Health endpoint
`GET /actuator/health` returns the application health status. Used by ECS for health
checks and load balancer routing.
