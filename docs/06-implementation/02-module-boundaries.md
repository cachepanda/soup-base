# Soup-Base — Module Boundaries

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## Backend Package Structure

```
dev.soupbase/
├── api/                    ← HTTP layer only. No business logic.
│   ├── AuthController      ← POST /api/auth/session
│   ├── DatabaseController  ← CRUD for /api/databases
│   └── dto/                ← Request/response records. No domain types exposed directly.
│
├── domain/                 ← All business logic lives here.
│   ├── UserService         ← user creation, lookup by clerk_id
│   ├── DatabaseService     ← create, list, delete, rotate credentials
│   ├── ProvisioningService ← orchestrates provisioning: calls infra + updates status
│   └── model/              ← Domain types (User, Database, DatabaseStatus, etc.)
│
├── db/                     ← Data access. jOOQ only.
│   ├── UserRepository
│   └── DatabaseRepository
│
├── infra/                  ← All external system calls.
│   ├── HostedClusterClient ← executes CREATE DATABASE, CREATE ROLE, DROP, etc. on hosted RDS
│   ├── PgBouncerClient     ← reloads PgBouncer config after provisioning changes
│   └── ClerkJwtFilter      ← validates Clerk JWT on every incoming request
│
└── config/                 ← Spring configuration classes.
    ├── SecurityConfig
    ├── AsyncConfig         ← thread pool for @Async provisioning
    └── ClerkConfig
```

---

## Rules

### Rule 1 — Controllers never contain business logic
Controllers extract request data, call one domain service method, and map the result to a
response DTO. No `if` branches on business state, no direct DB calls, no AWS SDK usage.

```java
// CORRECT
@PostMapping
public ResponseEntity<CreateDatabaseResponse> create(
        @RequestBody CreateDatabaseRequest req,
        @AuthenticationPrincipal ClerkPrincipal principal) {
    var db = databaseService.createDatabase(principal.clerkId(), req.name());
    return ResponseEntity.accepted().body(CreateDatabaseResponse.from(db));
}

// WRONG — business logic in controller
@PostMapping
public ResponseEntity<?> create(...) {
    if (databaseRepository.countByUserId(userId) >= 3) { ... }  // ← belongs in service
    ...
}
```

### Rule 2 — Domain services never call AWS SDK directly
All hosted cluster operations go through `HostedClusterClient` and `PgBouncerClient` in
the `infra/` package. Domain services depend on these interfaces, not on AWS or JDBC
directly.

```java
// CORRECT — domain service calls infra interface
provisioningService.provision(database);

// WRONG — domain service uses JDBC directly
jdbcTemplate.execute("CREATE DATABASE ...");  // ← belongs in HostedClusterClient
```

### Rule 3 — Repositories never access request-scoped state
`UserRepository` and `DatabaseRepository` receive all parameters explicitly (userId,
databaseId, etc.). They do not access `SecurityContextHolder` or any thread-local state.

### Rule 4 — DTOs stay in the api/ package
Domain types (`Database`, `User`) are never returned directly from controllers. Map to
response DTOs at the controller boundary. This prevents leaking internal fields
(e.g., `pgPasswordHash`) in API responses.

### Rule 5 — infra/ has no business logic
`HostedClusterClient` and `PgBouncerClient` are thin wrappers. They execute the SQL or
system call and return a result. They do not decide whether to proceed, retry, or fail.
That logic belongs in `ProvisioningService`.

---

## Frontend Module Structure

```
frontend/src/
├── pages/
│   ├── DashboardPage.tsx     ← database list
│   ├── DatabaseDetailPage.tsx
│   └── AuthPage.tsx          ← Clerk sign-in/sign-up
│
├── components/
│   ├── DatabaseCard.tsx
│   ├── CreateDatabaseModal.tsx
│   ├── ConnectionStringDisplay.tsx
│   └── StatusBadge.tsx
│
├── hooks/
│   ├── useDatabases.ts       ← TanStack Query: list, create, delete
│   ├── useDatabase.ts        ← TanStack Query: single database + polling
│   └── useRotateCredentials.ts
│
└── api/
    └── databases.ts          ← typed fetch wrappers. Zod schemas for responses.
```

### Frontend rules
- All server state (databases, status) managed by TanStack Query. No manual `useState` for
  server data.
- `api/` functions are the only place `fetch` is called. Components call hooks; hooks call
  `api/`.
- Polling for `PROVISIONING` / `DELETING` status is implemented in `useDatabase.ts` via
  TanStack Query's `refetchInterval` (2 seconds while status is transitional, off otherwise).
