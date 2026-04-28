# Soup-Base — Functional Requirements

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>
**Parent:** [PRD v1.0](../01-product/01-prd.md)<br>

---

## Auth (FR-AU)

### FR-AU-001 — User authentication via Clerk
The system uses Clerk for identity. The backend validates Clerk-issued JWTs on every
authenticated request. The frontend uses Clerk's prebuilt components for sign-up and sign-in.

**Acceptance criteria:**
- Requests without a valid Clerk JWT to any authenticated endpoint return `401 Unauthorized`.
- Requests with an expired JWT return `401 Unauthorized`.
- The authenticated user's Clerk `userId` is extracted from the JWT and used as the owner
  identifier for all database operations.
- The backend does not store passwords. User identity is fully delegated to Clerk.

### FR-AU-002 — User record creation on first login
On first successful authentication, if no user record exists in the control-plane database
for the Clerk `userId`, one is created automatically.

**Acceptance criteria:**
- `POST /api/auth/session` (called by the frontend after Clerk sign-in) creates a user
  record if one does not exist, then returns the user profile.
- Subsequent calls to the same endpoint return the existing record without modification.
- The operation is idempotent.

---

## Database Management (FR-DB)

### FR-DB-001 — Create a database
A logged-in user can provision a new Postgres database.

**Acceptance criteria:**
- `POST /api/databases` accepts an optional `name` field (max 40 chars, alphanumeric +
  hyphens). If omitted, a name is generated as `db-{adjective}-{noun}`.
- The endpoint returns `202 Accepted` immediately with a database record in
  `status: PROVISIONING`.
- Provisioning completes asynchronously. The database transitions to `status: ACTIVE`
  when the Postgres DATABASE and scoped role have been created on the hosted cluster.
- If a user already has 3 databases (in any non-DELETED status), the endpoint returns
  `422 Unprocessable Entity` with `error: DATABASE_LIMIT_REACHED`.
- Database names must be unique per user. A duplicate name returns `409 Conflict`.

### FR-DB-002 — View connection details
A logged-in user can retrieve the connection string and individual credentials for one
of their databases.

**Acceptance criteria:**
- `GET /api/databases/{id}` returns the database record including:
  - `connectionString`: full Postgres URI including credentials
  - `host`, `port`, `database`, `username`, `password` as individual fields
- Only the owner of the database can access its credentials. Any other authenticated user
  receives `404 Not Found` (not 403 — do not reveal existence).
- Credentials are only returned when `status` is `ACTIVE`. While `PROVISIONING`, the
  credentials fields are `null`.

### FR-DB-003 — List databases
A logged-in user can list all their databases.

**Acceptance criteria:**
- `GET /api/databases` returns an array of the user's databases, ordered by
  `created_at` descending.
- Each item includes `id`, `name`, `status`, `created_at`. Credentials are NOT included
  in list responses.
- Databases with `status: DELETED` are excluded from the list.

### FR-DB-004 — Delete a database
A logged-in user can delete one of their databases.

**Acceptance criteria:**
- `DELETE /api/databases/{id}` transitions the database to `status: DELETING` and returns
  `202 Accepted`.
- Deletion completes asynchronously. The Postgres DATABASE and role are dropped on the
  hosted cluster. The record transitions to `status: DELETED`.
- Only the owner can delete a database. Non-owner requests return `404 Not Found`.
- Deleting a database that is already `DELETING` or `DELETED` returns `409 Conflict`.
- Deleting a `PROVISIONING` database returns `409 Conflict` — user must wait for it to
  become `ACTIVE` first.

### FR-DB-005 — Database status polling
The frontend can poll the status of a database until it leaves `PROVISIONING` or `DELETING`.

**Acceptance criteria:**
- `GET /api/databases/{id}/status` returns `{ "status": "PROVISIONING" | "ACTIVE" |
  "DELETING" | "DELETED" | "FAILED" }`.
- The endpoint is lightweight — it does not return credentials.
- If provisioning fails, the status transitions to `FAILED` with a `failure_reason` field.
  The user can delete a `FAILED` database.

### FR-DB-006 — Rotate credentials
A logged-in user can rotate the password for one of their databases.

**Acceptance criteria:**
- `POST /api/databases/{id}/rotate-credentials` generates a new random password, updates
  the role on the hosted cluster, and returns the new credentials immediately.
- Only available when `status` is `ACTIVE`.
- The old password is immediately invalid after rotation.
- Only the owner can rotate credentials.

---

## Dashboard (FR-UI)

### FR-UI-001 — Dashboard page
After sign-in, the user sees a dashboard listing their databases with status indicators.

**Acceptance criteria:**
- Each database shows: name, status badge (Provisioning / Active / Deleting / Failed),
  and created date.
- A "New Database" button opens the creation flow.
- While a database is in `PROVISIONING` or `DELETING`, the dashboard polls status every
  2 seconds and updates the badge without a full page reload.

### FR-UI-002 — Database detail page
The user can click a database to see its connection details.

**Acceptance criteria:**
- Shows: connection string (with copy button), individual credential fields.
- Password is masked by default with a "reveal" toggle.
- A "Rotate Credentials" button triggers FR-DB-006 with a confirmation dialog.
- A "Delete Database" button triggers FR-DB-004 with a confirmation dialog.

### FR-UI-003 — New database form
The user can create a database from the dashboard.

**Acceptance criteria:**
- A name field (optional). Client-side validation: max 40 chars, alphanumeric + hyphens.
- "Create" button submits and immediately shows the new database in `PROVISIONING` state.
- If the user has reached the 3-database limit, the "New Database" button is disabled with
  a tooltip explaining the limit.
