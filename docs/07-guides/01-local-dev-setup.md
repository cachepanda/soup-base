# Soup-Base — Local Dev Setup

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## Prerequisites

- Docker Desktop
- Java 21 (recommend: `sdk install java 21-tem` via SDKMAN)
- Node.js 22 (`nvm install 22`)
- A Clerk account (free) — [clerk.com](https://clerk.com)

---

## 1. Start local infrastructure

```bash
cd infra/docker
docker compose up -d
```

This starts:
- **Control-plane Postgres** on `localhost:5432` — soup-base's own metadata DB
- **Hosted-cluster Postgres** on `localhost:5433` — where user databases are provisioned
- **PgBouncer** on `localhost:6432` — connection pooler in front of hosted cluster

---

## 2. Configure environment

```bash
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml
```

Fill in your Clerk publishable key and secret key from the Clerk dashboard.
`application-local.yml` is gitignored — never commit it.

Key values:
```yaml
clerk:
  publishable-key: pk_test_...
  secret-key: sk_test_...

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/soupbase
    username: soupbase
    password: localdev

soupbase:
  hosted:
    url: jdbc:postgresql://localhost:5433/postgres
    username: sb_admin
    password: localdev
```

---

## 3. Run migrations and codegen

```bash
cd backend
./gradlew flywayMigrate    # runs Flyway against control-plane Postgres
./gradlew jooqCodegen      # generates jOOQ classes from the migrated schema
```

Run these again any time a new migration is added.

---

## 4. Start the backend

```bash
cd backend
./gradlew bootRun
```

Backend starts on `http://localhost:8080`.
Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

## 5. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on `http://localhost:5173`. Vite proxies `/api/*` to the backend.

---

## 6. Run tests

```bash
# Backend — unit + architecture tests (fast, no Docker needed)
cd backend && ./gradlew test

# Backend — integration tests (requires Docker for Testcontainers)
cd backend && ./gradlew integrationTest

# Frontend
cd frontend && npm test
```

---

## Useful local commands

```bash
# Connect to control-plane DB
psql postgresql://soupbase:localdev@localhost:5432/soupbase

# Connect to hosted cluster (as admin)
psql postgresql://sb_admin:localdev@localhost:5433/postgres

# Connect via PgBouncer (as a provisioned user — fill in actual creds)
psql postgresql://{username}:{password}@localhost:6432/{database}

# Wipe and restart all containers + data
docker compose down -v && docker compose up -d
```

---

## Troubleshooting

**jOOQ codegen fails:** Make sure `docker compose up` is running and Flyway migrations
have been applied first.

**Backend fails to start:** Check `application-local.yml` exists and has valid Clerk keys.

**Tests fail with "connection refused":** Testcontainers manages its own Docker containers
— you don't need `docker compose` running for tests. If Docker Desktop isn't running at
all, start it.
