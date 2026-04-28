# Soup-Base — Coding Standards

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## Backend (Java / Spring Boot)

### General
- Java 21. Use records for DTOs and value types. Use `sealed` interfaces for result types.
- Virtual threads enabled globally (`spring.threads.virtual.enabled=true`). Do not create
  manual thread pools unless there is a specific reason.
- `Result<T>` pattern for service return types on write operations:
  ```java
  sealed interface Result<T> permits Result.Ok, Result.Err {
      record Ok<T>(T value) implements Result<T> {}
      record Err<T>(String code, String message) implements Result<T> {}
  }
  ```
- No checked exceptions crossing layer boundaries. Controllers catch `Result.Err` and map
  to HTTP responses. Services return `Result`, they do not throw.

### Naming
- Controllers: `{Resource}Controller` (e.g., `DatabaseController`)
- Services: `{Resource}Service` (e.g., `DatabaseService`)
- Repositories: `{Resource}Repository` (e.g., `DatabaseRepository`)
- DTOs: `{Action}{Resource}Request` / `{Action}{Resource}Response`
  (e.g., `CreateDatabaseRequest`, `CreateDatabaseResponse`)

### Database
- All schema changes via Flyway. File naming: `V{n}__{snake_case_description}.sql`.
  Example: `V3__add_failure_reason_to_databases.sql`
- jOOQ for all queries. No raw JDBC strings in domain or repository code.
- Run `./gradlew jooqCodegen` after every migration to regenerate type-safe classes.
- Never use `SELECT *` in jOOQ queries — always specify columns explicitly.

### Security
- `@PreAuthorize("isAuthenticated()")` on all controller methods by default.
- Public endpoints (e.g., `/actuator/health`) annotated `@PublicEndpoint` (custom annotation)
  and listed in `SecurityConfig`.
- Never log `pg_password`, `connectionString`, or any field ending in `_hash` or `_secret`.

---

## Frontend (React / TypeScript)

### General
- TypeScript strict mode. No `any`. No `@ts-ignore` without a comment explaining why.
- Functional components only. No class components.
- Tailwind CSS for styling. No inline styles. No CSS modules.

### State management
- TanStack Query for all server state. Query keys in `src/api/queryKeys.ts`.
- `useState` only for local UI state (modal open/closed, form field values).
- No Redux, Zustand, or other global state stores.

### API client
```typescript
// src/api/databases.ts — pattern for all resources

const DatabaseSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  status: z.enum(['PROVISIONING', 'ACTIVE', 'DELETING', 'DELETED', 'FAILED']),
  createdAt: z.string().datetime(),
  connectionString: z.string().nullable(),
  host: z.string().nullable(),
  port: z.number().nullable(),
  database: z.string().nullable(),
  username: z.string().nullable(),
});

export type Database = z.infer<typeof DatabaseSchema>;

export async function fetchDatabases(token: string): Promise<Database[]> {
  const res = await fetch('/api/databases', {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new ApiError(res.status, await res.json());
  return z.array(DatabaseSchema).parse(await res.json());
}
```

### Forms
- React Hook Form + Zod resolver for all forms.
- Show field-level validation errors inline, not as toasts.
- Disable submit button while request is in-flight (`isPending` from `useMutation`).

---

## Git conventions

- Branch naming: `feat/{short-description}`, `fix/{short-description}`, `docs/{short-description}`
- Commit messages: imperative mood, present tense. `Add database deletion endpoint` not
  `Added database deletion endpoint`.
- One logical change per PR. Separate migration PRs from feature PRs if the migration is
  large.
- PRs require CI to pass before merge. No `--no-verify`.
