package dev.soupbase.api;

import dev.soupbase.IntegrationTestBase;
import dev.soupbase.api.dto.CreateDatabaseRequest;
import dev.soupbase.api.dto.CreateDatabaseResponse;
import dev.soupbase.api.dto.SessionRequest;
import dev.soupbase.domain.model.DatabaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseControllerApiTest extends IntegrationTestBase {

    private String setupUser(String clerkId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken(clerkId));
        HttpEntity<SessionRequest> entity = new HttpEntity<>(new SessionRequest(clerkId + "@example.com"), headers);
        restTemplate.postForEntity("/api/auth/session", entity, Void.class);
        return clerkId;
    }

    private ResponseEntity<CreateDatabaseResponse> postCreate(String clerkId, CreateDatabaseRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken(clerkId));
        HttpEntity<CreateDatabaseRequest> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/api/databases", entity, CreateDatabaseResponse.class);
    }

    private ResponseEntity<Map<String, Object>> postCreateForError(String clerkId, CreateDatabaseRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken(clerkId));
        HttpEntity<CreateDatabaseRequest> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("/api/databases", HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {});
    }

    @Test
    void create_withName_returns202WithProvisioningRecord() {
        String clerkId = setupUser("db_ctrl_named");

        ResponseEntity<CreateDatabaseResponse> response = postCreate(clerkId, new CreateDatabaseRequest("my-database"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        CreateDatabaseResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.name()).isEqualTo("my-database");
        assertThat(body.status()).isEqualTo(DatabaseStatus.PROVISIONING);
        assertThat(body.id()).isNotNull();
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.connectionString()).isNull();
        assertThat(body.host()).isNull();
        assertThat(body.port()).isNull();
        assertThat(body.database()).isNull();
        assertThat(body.username()).isNull();
    }

    @Test
    void create_withoutName_returns202WithGeneratedName() {
        String clerkId = setupUser("db_ctrl_noname");

        ResponseEntity<CreateDatabaseResponse> response = postCreate(clerkId, new CreateDatabaseRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        CreateDatabaseResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.name()).startsWith("db-");
        assertThat(body.status()).isEqualTo(DatabaseStatus.PROVISIONING);
        assertThat(body.id()).isNotNull();
    }

    @Test
    void create_withoutAuth_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateDatabaseRequest> entity = new HttpEntity<>(new CreateDatabaseRequest("test"), headers);

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/databases", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_exceedingLimit_returns422WithErrorCode() {
        String clerkId = setupUser("db_ctrl_limit");
        postCreate(clerkId, new CreateDatabaseRequest("limit-db-1"));
        postCreate(clerkId, new CreateDatabaseRequest("limit-db-2"));
        postCreate(clerkId, new CreateDatabaseRequest("limit-db-3"));

        ResponseEntity<Map<String, Object>> response = postCreateForError(clerkId, new CreateDatabaseRequest("limit-db-4"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("error", "DATABASE_LIMIT_REACHED");
    }

    @Test
    void create_duplicateName_returns409WithErrorCode() {
        String clerkId = setupUser("db_ctrl_dup");
        postCreate(clerkId, new CreateDatabaseRequest("duplicate-db"));

        ResponseEntity<Map<String, Object>> response = postCreateForError(clerkId, new CreateDatabaseRequest("duplicate-db"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "DUPLICATE_NAME");
    }
}
