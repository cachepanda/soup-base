package dev.soupbase.infra;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClerkJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClerkJwtFilter.class);

    private final String jwksUrl;
    private final String issuer;
    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;

    // Replaced atomically on JWKS refresh; individual entries are never mutated.
    private volatile Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public ClerkJwtFilter(
            @Value("${clerk.jwks-url}") String jwksUrl,
            @Value("${clerk.issuer}") String issuer,
            @Lazy RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper) {
        this.jwksUrl = jwksUrl;
        this.issuer = issuer;
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isPublicEndpoint(request)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        String token = header.substring(7);
        try {
            DecodedJWT decoded = JWT.decode(token);
            RSAPublicKey publicKey = resolvePublicKey(decoded);

            JWTVerifier verifier = JWT.require(Algorithm.RSA256(publicKey, null))
                    .withIssuer(issuer)
                    .build();

            DecodedJWT verified = verifier.verify(token);
            ClerkPrincipal principal = new ClerkPrincipal(verified.getSubject());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (JWTVerificationException e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        } catch (Exception e) {
            log.error("JWT processing error", e);
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod method) {
                return method.hasMethodAnnotation(PublicEndpoint.class)
                        || method.getBeanType().isAnnotationPresent(PublicEndpoint.class);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private RSAPublicKey resolvePublicKey(DecodedJWT jwt) throws Exception {
        String kid = jwt.getKeyId();
        if (kid != null && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }
        Map<String, RSAPublicKey> fresh = fetchJwks();
        keyCache = new ConcurrentHashMap<>(fresh);
        if (kid != null && fresh.containsKey(kid)) {
            return fresh.get(kid);
        }
        if (!fresh.isEmpty()) {
            return fresh.values().iterator().next();
        }
        throw new IllegalStateException("No RSA public key found in JWKS");
    }

    private Map<String, RSAPublicKey> fetchJwks() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        JsonNode root = objectMapper.readTree(res.body());
        JsonNode keys = root.get("keys");

        Map<String, RSAPublicKey> result = new HashMap<>();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        for (JsonNode key : keys) {
            if (!"RSA".equals(key.path("kty").asText())) continue;
            String keyId = key.path("kid").asText("");
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("n").asText()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("e").asText()));
            result.put(keyId, (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
        }
        return result;
    }
}
