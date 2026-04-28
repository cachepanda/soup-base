# Soup-Base — Tech Stack

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## Backend

| Component | Choice | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.4.x |
| Build tool | Gradle | 8.x (Kotlin DSL) |
| Database access | jOOQ | 3.19.x |
| Migrations | Flyway | 10.x |
| Connection pool | HikariCP | (bundled with Spring Boot) |
| Auth | Clerk JWT validation | `com.auth0:java-jwt:4.x` |
| AWS SDK | AWS SDK for Java v2 | 2.x (only `rds-data`, `secretsmanager`) |
| HTTP client | Spring `RestClient` | (bundled with Spring Boot) |
| Testing | JUnit 5 + AssertJ + Testcontainers | latest |
| Containerisation | Spring Boot Docker buildpacks | — |

**Key Gradle dependencies:**

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jooq:jooq:3.19.+")
    implementation("org.flywaydb:flyway-core:10.+")
    implementation("org.flywaydb:flyway-database-postgresql:10.+")
    implementation("com.auth0:java-jwt:4.+")
    implementation("software.amazon.awssdk:secretsmanager:2.+")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
```

**Java version flag:** `sourceCompatibility = JavaVersion.VERSION_21`<br>
**Virtual threads:** `spring.threads.virtual.enabled=true` in `application.yml`

---

## Frontend

| Component | Choice | Version |
|---|---|---|
| Language | TypeScript | 5.x |
| Framework | React | 18.x |
| Build tool | Vite | 5.x |
| Styling | Tailwind CSS | 3.x |
| Server state | TanStack Query | 5.x |
| Forms | React Hook Form + Zod | 7.x / 3.x |
| Auth components | Clerk React SDK | `@clerk/clerk-react` latest |
| Routing | React Router | 6.x |
| Testing | Vitest | latest |

**Key `package.json` dependencies:**

```json
{
  "dependencies": {
    "react": "^18.0.0",
    "react-dom": "^18.0.0",
    "react-router-dom": "^6.0.0",
    "@tanstack/react-query": "^5.0.0",
    "react-hook-form": "^7.0.0",
    "zod": "^3.0.0",
    "@hookform/resolvers": "^3.0.0",
    "@clerk/clerk-react": "latest"
  },
  "devDependencies": {
    "typescript": "^5.0.0",
    "vite": "^5.0.0",
    "tailwindcss": "^3.0.0",
    "vitest": "latest",
    "@vitejs/plugin-react": "latest"
  }
}
```

---

## Infrastructure

| Component | Choice |
|---|---|
| Compute | AWS ECS Fargate |
| Control-plane DB | AWS RDS PostgreSQL 16 |
| Hosted DB cluster | AWS RDS PostgreSQL 16 (separate instance) |
| Connection pooler | PgBouncer 1.22 (ECS task, sidecar to app) |
| CDN / static hosting | Firebase Hosting (frontend SPA) |
| Container registry | Amazon ECR |
| IaC | AWS CDK v2 (TypeScript) |
| Secrets | AWS Secrets Manager |
| DNS | Route 53 |

---

## Local Development

| Component | How |
|---|---|
| Control-plane Postgres | Docker (via docker-compose) |
| Hosted cluster Postgres | Docker (via docker-compose, separate container) |
| PgBouncer | Docker (via docker-compose) |
| Backend | `./gradlew bootRun` |
| Frontend | `npm run dev` (Vite, port 5173) |
| Auth (local) | Clerk dev instance (free, requires Clerk account) |

All local dependencies are defined in `infra/docker/docker-compose.yml`.
No AWS credentials needed for local development.
