package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "test-jwt-secret-32-chars-do-not-leak!";
    private static final long EXPIRATION_MS = 3_600_000L; // 1h

    private JwtTokenService service;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        service = new JwtTokenService(SECRET, EXPIRATION_MS);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    void generateToken_carriesAnonymousIdAsSubject() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String token = service.generateToken(anonymousId, auth);
        Claims claims = parse(token);

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    void generateToken_carriesAllAuthoritiesInPermissionsClaim() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(
                        new SimpleGrantedAuthority("ROLE_HEALTH_STAFF"),
                        new SimpleGrantedAuthority("identity:lookup"),
                        new SimpleGrantedAuthority("survey:validate")
                ));

        String token = service.generateToken(anonymousId, auth);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) parse(token).get("permissions");

        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("ROLE_HEALTH_STAFF"));
        assertTrue(permissions.contains("identity:lookup"));
        assertTrue(permissions.contains("survey:validate"));
    }

    @Test
    void generateToken_setsExpirationApproximatelyAtExpectedOffset() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "u", null, List.of());

        long beforeMs = System.currentTimeMillis();
        String token = service.generateToken(anonymousId, auth);
        Claims claims = parse(token);

        long expectedExpiry = beforeMs + EXPIRATION_MS;
        long actualExpiry = claims.getExpiration().getTime();
        // Allow 2s drift for the JWT building + parsing roundtrip
        assertTrue(Math.abs(actualExpiry - expectedExpiry) < 2000,
                "expiration must be ~now + EXPIRATION_MS; drift was " +
                        (actualExpiry - expectedExpiry) + "ms");
    }

    @Test
    void generateToken_isSignedSoTamperingFailsVerification() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "u", null, List.of());

        String token = service.generateToken(anonymousId, auth);
        String[] parts = token.split("\\.");
        // Tamper with the payload (replace last char) -> signature mismatch.
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + "X." + parts[2];

        assertThrows(io.jsonwebtoken.JwtException.class, () -> parse(tampered));
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
