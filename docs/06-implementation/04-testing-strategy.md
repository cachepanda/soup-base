# Soup-Base — Testing Strategy

**Version:** 1.0<br>
**Status:** Draft<br>
**Last updated:** April 2026<br>

---

## 1. Principles

1. **Integration tests are primary.** Tests run against real Postgres via Testcontainers.
   No mocking of repositories or services.
2. **One principled mock:** `HostedClusterClient` is mocked in API tests. The real
   implementation executes DDL on a live Postgres database — integration tests for
   `HostedClusterClient` itself use Testcontainers, but API-layer tests use a fake
   implementation that records calls.
3. **No test requires a real Clerk account.** The `ClerkJwtFilter` is replaceable with a
   test filter that accepts a pre-signed test JWT for a synthetic user.
4. **Every endpoint has three tests minimum:** happy path, unauthenticated (401), and
   not-found or forbidden (404).

---

## 2. Test Layers

| Layer | Technology | What it tests | Count |
|---|---|---|---|
| Unit | JUnit 5 | Password generation, name generation algorithm | < 10 |
| Integration | Testcontainers (Postgres) | Repository methods, ProvisioningService, HostedClusterClient | 20–40 |
| API | MockMvcTester + Testcontainers | Full HTTP → controller → service → DB | 30–50 |

No E2E browser tests in v1. Manual testing suffices at this scale.

---

## 3. Test Infrastructure

### Shared Testcontainers setup

```java
public abstract class IntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> controlPlane = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("soupbase_test")
        .withUsername("soupbase")
        .withPassword("test");

    @Container
    static final PostgreSQLContainer<?> hostedCluster = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("postgres")
        .withUsername("sb_admin")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", controlPlane::getJdbcUrl);
        registry.add("spring.datasource.username", controlPlane::getUsername);
        registry.add("spring.datasource.password", controlPlane::getPassword);
        registry.add("soupbase.hosted.url", hostedCluster::getJdbcUrl);
        registry.add("soupbase.hosted.username", hostedCluster::getUsername);
        registry.add("soupbase.hosted.password", hostedCluster::getPassword);
    }
}
```

### Test auth — fake Clerk JWT filter

```java
@TestConfiguration
class TestSecurityConfig {
    @Bean @Primary
    ClerkJwtFilter testClerkFilter() {
        // Accepts "Bearer test-user-{clerkId}" as a valid token in tests
        return new FakeClerkJwtFilter();
    }
}
```

API tests pass `Authorization: Bearer test-user-user_abc123` to authenticate as a
synthetic user without a real Clerk account.

---

## 4. Key Test Cases

### FR-DB-001 — Create database

```java
@Test
void createDatabase_withValidAuth_returns202AndProvisioningStatus() { ... }

@Test
void createDatabase_withoutAuth_returns401() { ... }

@Test
void createDatabase_whenLimitReached_returns422WithDatabaseLimitReached() { ... }

@Test
void createDatabase_withDuplicateName_returns409() { ... }
```

### FR-DB-002 — Credentials access

```java
@Test
void getDatabase_asOwner_returnsCredentials() { ... }

@Test
void getDatabase_asOtherUser_returns404() { ... }  // not 403 — do not reveal existence

@Test
void getDatabase_whileProvisioning_returnsNullCredentials() { ... }
```

### FR-DB-004 — Delete database

```java
@Test
void deleteDatabase_asOwner_returns202AndDeletingStatus() { ... }

@Test
void deleteDatabase_asOtherUser_returns404() { ... }

@Test
void deleteDatabase_alreadyDeleting_returns409() { ... }

@Test
void deleteDatabase_whileProvisioning_returns409() { ... }
```

### Provisioning integration

```java
@Test
void provision_createsPostgresDatabaseAndRole_onHostedCluster() {
    // Uses real hostedCluster Testcontainer
    // Verifies: pg_database exists, pg_role exists, role can connect
}

@Test
void provision_whenDatabaseAlreadyExists_isIdempotent() {
    // Simulates crash-recovery: provision called twice for same record
    // Verifies: no duplicate error, status ends ACTIVE
}
```

### Credential rotation

```java
@Test
void rotateCredentials_updatesRolePassword_oldPasswordInvalid() { ... }

@Test
void rotateCredentials_whileProvisioning_returns409() { ... }
```

---

## 5. What Is NOT Tested

- Clerk JWT signature verification (Clerk's SDK handles this; we trust it)
- PgBouncer config reload (tested manually; too complex to automate in v1)
- Frontend components (manual testing in v1)
