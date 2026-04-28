# Soup-Base — Data Model

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>
**Parent:** [ADRs v1.0](./01-adrs.md)<br>

This document describes the **control-plane** database schema — soup-base's own Postgres
instance that tracks users and their provisioned databases. It does not describe the
user databases themselves (those are isolated Postgres DATABASEs on the hosted cluster).

---

## Tables

### `users`

Stores one record per authenticated user. Created on first successful Clerk sign-in.

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_id    TEXT NOT NULL UNIQUE,   -- Clerk userId, used as FK for ownership
    email       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_clerk_id ON users (clerk_id);
```

| Column | Notes |
|---|---|
| `id` | Internal UUID. Not exposed in APIs — `clerk_id` is the public identifier. |
| `clerk_id` | The Clerk `userId`. Immutable once set. |
| `email` | Stored for display. Source of truth is Clerk. |

---

### `databases`

One row per user-provisioned database. Tracks the lifecycle from provisioning to deletion.

```sql
CREATE TYPE database_status AS ENUM (
    'PROVISIONING',
    'ACTIVE',
    'DELETING',
    'DELETED',
    'FAILED'
);

CREATE TABLE databases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    name                TEXT NOT NULL,                  -- user-visible name
    pg_database_name    TEXT NOT NULL UNIQUE,           -- actual name on hosted cluster: sb_{hash}
    pg_username         TEXT NOT NULL UNIQUE,           -- scoped role on hosted cluster
    pg_password_hash    TEXT NOT NULL,                  -- bcrypt hash of the role password
    status              database_status NOT NULL DEFAULT 'PROVISIONING',
    failure_reason      TEXT,                           -- set when status = FAILED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_databases_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_databases_user_id ON databases (user_id);
CREATE INDEX idx_databases_status  ON databases (status) WHERE status IN ('PROVISIONING', 'DELETING');
```

| Column | Notes |
|---|---|
| `name` | User-visible label, e.g. `my-app-db`. Unique per user. |
| `pg_database_name` | Internal name on hosted cluster, e.g. `sb_a3f2c_01jx...`. Never shown as-is. |
| `pg_username` | Postgres role name, e.g. `sb_user_a3f2c_01jx...`. Matches `pg_database_name`. |
| `pg_password_hash` | The current password is bcrypt-hashed for storage. Plaintext only available at creation/rotation. |
| `failure_reason` | Human-readable reason if provisioning or deletion fails. |

**Status transitions:**

```
PROVISIONING → ACTIVE      (provisioning succeeded)
PROVISIONING → FAILED      (provisioning failed)
ACTIVE       → DELETING    (user requests deletion)
DELETING     → DELETED     (deletion succeeded)
DELETING     → FAILED      (deletion failed — unusual)
FAILED       → DELETING    (user deletes a failed database)
```

---

## Hosted Cluster Conventions

The hosted RDS cluster is managed by soup-base but is separate from the control-plane.
The following naming convention applies to objects created there:

| Object | Pattern | Example |
|---|---|---|
| Database | `sb_{6-char-user-hash}_{database-uuid-short}` | `sb_a3f2c1_01jxmk` |
| Role (username) | `sb_u_{6-char-user-hash}_{database-uuid-short}` | `sb_u_a3f2c1_01jxmk` |

The 6-char user hash is the first 6 characters of the MD5 of the user's `clerk_id`.
This keeps names short while preventing collisions across users.

The role has `CONNECT` privilege on its own database and `ALL PRIVILEGES` on the public
schema of that database. It has no access to any other database or the `postgres` database.

---

## What is NOT in this schema

- **Session tokens**: auth is fully delegated to Clerk. No session table.
- **Billing / usage meters**: out of scope for v1.
- **Audit log**: out of scope for v1 (add when compliance matters).
- **Backups**: managed at the RDS level, not tracked in the app schema.
