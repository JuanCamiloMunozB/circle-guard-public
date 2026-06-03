package com.circleguard.identity.integration;

import com.circleguard.identity.service.IdentityVaultService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the identity-service persistence layer.
 *
 * Uses H2 (MODE=PostgreSQL) configured in src/test/resources/application.yml —
 * no Docker required. The full Spring context boots with Flyway disabled so
 * Hibernate generates the schema directly from entity definitions, exercising
 * IdentityEncryptionConverter, IdentityVaultService and the JPA repository
 * end-to-end against an in-process database.
 *
 * KafkaTemplate is mocked to isolate from the message broker.
 *
 * Tagged @Tag("integration") so it only runs under
 * {@code ./gradlew integrationTest}, never as part of the regular {@code test} task.
 */
@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // AES/CBC encryption requires a hex-encoded salt >= 8 bytes (16 hex chars).
                "vault.secret=test-vault-password",
                "vault.salt=deadbeefcafe00110011deadbeefcafe",
                "vault.hash-salt=test-hash-salt",
                // JwtAuthenticationFilter requires a key of at least 32 bytes for HS256.
                "jwt.secret=test-jwt-secret-key-that-is-long-enough-",
                // Disable Flyway; let Hibernate generate the schema from entity definitions.
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                // Kafka connections are lazy — this placeholder prevents autoconfiguration
                // from failing at startup. KafkaTemplate is replaced by @MockitoBean below.
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
class IdentityVaultPostgresIntegrationTest {

    @MockitoBean
    @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private IdentityVaultService vaultService;

    // ── test cases ────────────────────────────────────────────────────────────

    @Test
    void getOrCreateAnonymousId_newIdentity_persistsAndReturnsUuid() {
        UUID id = vaultService.getOrCreateAnonymousId("student@university.edu");

        assertNotNull(id, "A new identity must be assigned a non-null anonymous UUID");
    }

    @Test
    void getOrCreateAnonymousId_sameIdentityTwice_returnsStableAnonymousId() {
        String identity = "professor@university.edu";
        UUID first = vaultService.getOrCreateAnonymousId(identity);
        UUID second = vaultService.getOrCreateAnonymousId(identity);

        assertEquals(first, second,
                "The same real identity must always map to the same anonymous ID (idempotent)");
    }

    @Test
    void getOrCreateAnonymousId_differentIdentities_returnDistinctIds() {
        UUID idA = vaultService.getOrCreateAnonymousId("alice@university.edu");
        UUID idB = vaultService.getOrCreateAnonymousId("bob@university.edu");

        assertNotEquals(idA, idB, "Different identities must map to different anonymous IDs");
    }

    @Test
    void resolveRealIdentity_roundTrip_decryptsCorrectly() {
        String original = "health-officer@university.edu";
        UUID anonymousId = vaultService.getOrCreateAnonymousId(original);

        String resolved = vaultService.resolveRealIdentity(anonymousId);

        assertEquals(original, resolved,
                "AES round-trip must preserve the original real identity after decrypt");
    }

    @Test
    void resolveRealIdentity_visitorCompositeKey_roundTrip() {
        String compositeIdentity = "VISITOR|visitor@external.org|John Doe|Campus Tour";
        UUID anonymousId = vaultService.getOrCreateAnonymousId(compositeIdentity);

        String resolved = vaultService.resolveRealIdentity(anonymousId);

        assertEquals(compositeIdentity, resolved,
                "Visitor composite-key identity must survive the full persist/decrypt cycle");
    }

    @Test
    void resolveRealIdentity_unknownUuid_throwsNotFound() {
        assertThrows(ResponseStatusException.class,
                () -> vaultService.resolveRealIdentity(UUID.randomUUID()),
                "Lookup of an unknown anonymous ID must throw a 404 ResponseStatusException");
    }
}
