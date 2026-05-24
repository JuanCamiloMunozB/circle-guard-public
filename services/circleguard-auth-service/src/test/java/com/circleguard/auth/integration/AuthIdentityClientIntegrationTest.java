package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the auth-service -> identity-service REST link.
 *
 * Spins up a real Docker container (Testcontainers + MockServer) that emulates
 * identity-service's /api/v1/identities/map endpoint. The auth-service
 * IdentityClient is wired against the container and makes actual HTTP calls,
 * exercising serialization, transport, and the Circuit Breaker fallback in a
 * realistic setup (no Mockito stub of RestTemplate).
 *
 * Requires Docker. Tagged @Tag("integration") so it only runs under
 * `./gradlew integrationTest`, never as part of the regular `test` task.
 */
@Testcontainers
@Tag("integration")
class AuthIdentityClientIntegrationTest {

    private static final MockServerContainer mockServer = new MockServerContainer(
            DockerImageName.parse("mockserver/mockserver:5.15.0"));

    private static MockServerClient mockServerClient;
    private static IdentityClient identityClient;

    @BeforeAll
    static void startContainer() {
        mockServer.start();
        mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
        // Build the IdentityClient against the running container's URL.
        String baseUrl = "http://" + mockServer.getHost() + ":" + mockServer.getServerPort();
        identityClient = new IdentityClient(new RestTemplate(), baseUrl);
    }

    @AfterAll
    static void stopContainer() {
        if (mockServer.isRunning()) {
            mockServer.stop();
        }
    }

    @Test
    void getAnonymousId_realHttpCall_returnsResolvedUuid() {
        UUID expected = UUID.randomUUID();
        mockServerClient.when(HttpRequest.request()
                        .withMethod("POST")
                        .withPath("/api/v1/identities/map"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withContentType(org.mockserver.model.MediaType.APPLICATION_JSON)
                        .withBody("{\"anonymousId\":\"" + expected + "\"}"));

        Optional<UUID> result = identityClient.getAnonymousId("student@university.edu");

        assertTrue(result.isPresent(), "happy-path response must yield a UUID");
        assertEquals(expected, result.get());
    }

    @Test
    void getAnonymousId_identityReturns5xx_circuitBreakerFallbackToEmpty() {
        // Reset previous expectations so this scenario hits the new rule
        mockServerClient.reset();
        mockServerClient.when(HttpRequest.request()
                        .withMethod("POST")
                        .withPath("/api/v1/identities/map"))
                .respond(HttpResponse.response()
                        .withStatusCode(500)
                        .withBody("upstream failure"));

        Optional<UUID> result = identityClient.getAnonymousId("student@university.edu");

        // The IdentityClient is wrapped in a Resilience4j Circuit Breaker (HU-06):
        // when the downstream replies 5xx, the fallback MUST return Optional.empty()
        // instead of propagating the exception to the caller.
        assertTrue(result.isEmpty(),
                "5xx from identity-service must degrade via Circuit Breaker fallback, not throw");
    }
}
