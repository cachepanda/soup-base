package dev.soupbase;

import dev.soupbase.infra.ClerkJwtFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.soupbase.infra.ClerkPrincipal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {

    private static final String TEST_TOKEN_PREFIX = "Bearer test-user-";

    @Container
    static final PostgreSQLContainer<?> controlPlane = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("soupbase_test")
            .withUsername("soupbase")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", controlPlane::getJdbcUrl);
        registry.add("spring.datasource.username", controlPlane::getUsername);
        registry.add("spring.datasource.password", controlPlane::getPassword);
        registry.add("clerk.jwks-url", () -> "https://test.clerk.dev/.well-known/jwks.json");
        registry.add("clerk.issuer", () -> "https://test.clerk.dev");
    }

    @MockBean
    ClerkJwtFilter clerkJwtFilter;

    @Autowired
    protected TestRestTemplate restTemplate;

    @BeforeEach
    void configureFakeAuth() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = (HttpServletRequest) invocation.getArgument(0);
            FilterChain chain = invocation.getArgument(2);

            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(TEST_TOKEN_PREFIX)) {
                String clerkId = header.substring(TEST_TOKEN_PREFIX.length());
                ClerkPrincipal principal = new ClerkPrincipal(clerkId);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            } else {
                SecurityContextHolder.clearContext();
            }

            chain.doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(clerkJwtFilter).doFilter(any(), any(), any());
    }

    protected static String bearerToken(String clerkId) {
        return TEST_TOKEN_PREFIX + clerkId;
    }
}
