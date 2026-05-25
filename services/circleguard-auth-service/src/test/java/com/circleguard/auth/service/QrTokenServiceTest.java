package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrTokenServiceTest {

    private static final String SECRET = "qr-test-secret-32-chars-keep-secret!";

    @Test
    void generateQrToken_carriesAnonymousIdAsSubjectAndIsSignedByQrSecret() {
        QrTokenService service = new QrTokenService(SECRET, 60_000L);
        UUID anonymousId = UUID.randomUUID();

        String token = service.generateQrToken(anonymousId);
        Claims claims = parse(token);

        assertEquals(anonymousId.toString(), claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().getTime() > claims.getIssuedAt().getTime(),
                "expiration must be strictly after iat");
    }

    @Test
    void generateQrToken_expiresInApproximatelyConfiguredWindow() {
        long expirationMs = 30_000L;
        QrTokenService service = new QrTokenService(SECRET, expirationMs);
        long before = System.currentTimeMillis();

        String token = service.generateQrToken(UUID.randomUUID());
        Claims claims = parse(token);

        long delta = claims.getExpiration().getTime() - before;
        assertTrue(delta > expirationMs - 2000 && delta < expirationMs + 2000,
                "QR expiration window drift: " + delta + " vs expected " + expirationMs);
    }

    @Test
    void generateQrToken_signaturesAreVerifiableOnlyWithSameSecret() {
        QrTokenService service = new QrTokenService(SECRET, 60_000L);

        String token = service.generateQrToken(UUID.randomUUID());

        // Wrong secret => signature mismatch
        Key wrongKey = Keys.hmacShaKeyFor("a-different-secret-32-chars-long-aaaaa".getBytes());
        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> Jwts.parserBuilder().setSigningKey(wrongKey).build().parseClaimsJws(token));
    }

    private Claims parse(String token) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}
