# Soup-Base — Product Requirements Document

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## 1. Problem

Developers building side projects, prototypes, or small apps need a database immediately.
Setting up Postgres locally is fine for dev, but hosting it for production means managing
an RDS instance, configuring security groups, handling backups, and paying for always-on
compute — all before writing a single line of application code.

Existing solutions (Supabase, Neon, Railway) solve this but come with feature surface area
that adds complexity and cost for users who just need a connection string.

---

## 2. Solution

Soup-Base provisions a hosted Postgres database in under 10 seconds. The user gets a
connection string. That's it. No clusters, no projects, no regions to pick, no storage
classes. Just a database.

---

## 3. Target Users

- Indie developers building side projects
- Students learning backend development
- Developers who need a throwaway database for a prototype

Not targeting: enterprise teams, high-traffic production apps, users who need
Postgres extensions, custom configurations, or compliance certifications.

---

## 4. Core User Journey

```
1. User visits soup-base.dev
2. User signs up with GitHub or email (Clerk)
3. User clicks "New Database"
4. User gives it a name (optional)
5. Database is provisioned in < 10 seconds
6. User sees: connection string, host, port, database name, username, password
7. User copies the connection string and uses it in their app
8. User can return to the dashboard to view, manage, or delete their databases
```

---

## 5. MVP Feature Set

### Must have (v1)
- Sign up / log in via Clerk (GitHub OAuth + email/password)
- Create a database (provisions real Postgres database on shared cluster)
- View connection string and individual credentials
- List all databases for the logged-in user
- Delete a database
- Database status indicator (provisioning → active → deleting)

### Out of scope for v1
- Billing / paid tiers (everything is free, usage capped at 3 databases per user)
- Backups and point-in-time recovery
- Database metrics or query analytics
- Custom Postgres extensions
- Multiple regions
- Direct SQL editor in the browser
- Team / org sharing of databases
- API keys (auth is UI-only for v1)

---

## 6. Success Metrics (v1)

- Time from "New Database" click to connection string displayed: < 10 seconds
- Zero cross-tenant data access incidents
- User can connect from a standard Postgres client using the provided string

---

## 7. Constraints

- Shared RDS cluster: databases are isolated at the Postgres DATABASE level (separate
  database + dedicated role per user project) on a single shared RDS instance.
- Hard limit: 3 databases per user in v1 (no billing enforcement, just a count check).
- Storage: no per-database storage enforcement in v1. Reasonable use assumed.
- Connection limit: 5 concurrent connections per database (enforced by PgBouncer).
