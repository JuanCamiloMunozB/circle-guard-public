package com.circleguard.gateway.integration;

import com.circleguard.gateway.service.QrValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the gateway-service → Redis health-status lookup.
 *
 * Spins up a real Redis 7 container (Testcontainers) and wires
 * QrValidationService against it. The JWT is signed with a local test key
 * so the full token-validation + Redis-lookup code path is exercised on real
 * infrastructure — no Mockito stub of StringRedisTemplate.
 *
 * Requires Docker. Tagged @Tag("integration") so it only runs under
 * {@code ./gradlew integrationTest}, never as part of the regular {@code test} task.
 */
@Testcontainers
@Tag("integration")
class GatewayRedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    // 32-byte key satisfies HMAC-SHA256 minimum length requirement.
    private static final String SECRET = "circleguard-qr-secret-key-32bytes!";
    private static final Key SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static QrValidationService service;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", SECRET);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String buildToken(String anonymousId) {
        return Jwts.builder()
                .setSubject(anonymousId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(SIGNING_KEY)
                .compact();
    }

    // ── test cases ────────────────────────────────────────────────────────────

    @Test
    void validate_noRedisEntry_grantsGreenAccess() {
        String id = UUID.randomUUID().toString();
        // No key in Redis → user has no elevated health status.
        QrValidationService.ValidationResult result = service.validateToken(buildToken(id));

        assertTrue(result.valid(), "User with no Redis entry must be allowed onto campus");
        assertEquals("GREEN", result.status());
    }

    @Test
    void validate_contagiedStatus_blocksAccess() {
        String id = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + id, "CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(id));

        assertFalse(result.valid(), "CONTAGIED user must be denied campus access");
        assertEquals("RED", result.status());
    }

    @Test
    void validate_potentialStatus_blocksAccess() {
        String id = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + id, "POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(id));

        assertFalse(result.valid(), "POTENTIAL user must be denied campus access");
        assertEquals("RED", result.status());
    }

    @Test
    void validate_activeStatusInRedis_grantsAccess() {
        String id = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + id, "ACTIVE");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(id));

        assertTrue(result.valid(), "ACTIVE user must be granted campus access");
        assertEquals("GREEN", result.status());
    }

    @Test
    void validate_tamperedToken_returnsRedInvalidToken() {
        QrValidationService.ValidationResult result = service.validateToken("not.a.valid.jwt");

        assertFalse(result.valid(), "Malformed JWT must be rejected");
        assertEquals("RED", result.status());
    }

    @Test
    void validate_expiredToken_returnsRedInvalidToken() {
        String expiredToken = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(SIGNING_KEY)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(expiredToken);

        assertFalse(result.valid(), "Expired JWT must be rejected");
        assertEquals("RED", result.status());
    }
}
