package dev.soupbase.api;

import dev.soupbase.IntegrationTestBase;
import dev.soupbase.api.dto.SessionRequest;
import dev.soupbase.api.dto.SessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerApiTest extends IntegrationTestBase {

    private static final String CLERK_ID = "user_test_abc123";
    private static final String EMAIL = "test@example.com";

    @Test
    void session_withValidAuth_createsAndReturnsUserProfile() {
        ResponseEntity<SessionResponse> response = postSession(CLERK_ID, EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.clerkId()).isEqualTo(CLERK_ID);
        assertThat(body.email()).isEqualTo(EMAIL);
        assertThat(body.id()).isNotNull();
        assertThat(body.createdAt()).isNotNull();
    }

    @Test
    void session_calledTwice_isIdempotentAndReturnsExistingRecord() {
        ResponseEntity<SessionResponse> first = postSession(CLERK_ID + "_idem", EMAIL);
        ResponseEntity<SessionResponse> second = postSession(CLERK_ID + "_idem", EMAIL);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        SessionResponse firstBody = first.getBody();
        SessionResponse secondBody = second.getBody();
        assertThat(firstBody).isNotNull();
        assertThat(secondBody).isNotNull();
        assertThat(secondBody.id()).isEqualTo(firstBody.id());
        assertThat(secondBody.clerkId()).isEqualTo(firstBody.clerkId());
        assertThat(secondBody.createdAt()).isEqualTo(firstBody.createdAt());
    }

    @Test
    void session_withoutAuth_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SessionRequest> entity = new HttpEntity<>(new SessionRequest(EMAIL), headers);

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/auth/session", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<SessionResponse> postSession(String clerkId, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken(clerkId));
        HttpEntity<SessionRequest> entity = new HttpEntity<>(new SessionRequest(email), headers);
        return restTemplate.postForEntity("/api/auth/session", entity, SessionResponse.class);
    }
}
