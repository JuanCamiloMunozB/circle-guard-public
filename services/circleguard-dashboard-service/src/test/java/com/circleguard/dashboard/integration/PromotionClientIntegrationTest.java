package com.circleguard.dashboard.integration;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the dashboard-service → promotion-service REST link.
 *
 * Spins up a real MockServer container (Testcontainers) that emulates
 * promotion-service's health-status endpoints. PromotionClient is wired
 * directly against the container URL so that the full HTTP round-trip —
 * including JSON deserialization — is exercised on real infrastructure
 * rather than with a Mockito stub of RestTemplate.
 *
 * Circuit-breaker fallback behaviour (AOP-dependent) is covered separately
 * by PromotionClientCircuitBreakerTest (unit test). This test focuses on
 * the happy-path contract between both services.
 *
 * Requires Docker. Tagged @Tag("integration") so it only runs under
 * {@code ./gradlew integrationTest}, never as part of the regular {@code test} task.
 */
@Testcontainers
@Tag("integration")
class PromotionClientIntegrationTest {

    private static final MockServerContainer MOCK_SERVER = new MockServerContainer(
            DockerImageName.parse("mockserver/mockserver:5.15.0"));

    private static MockServerClient mockServerClient;
    private static PromotionClient promotionClient;

    @BeforeAll
    static void startContainer() {
        MOCK_SERVER.start();
        mockServerClient = new MockServerClient(MOCK_SERVER.getHost(), MOCK_SERVER.getServerPort());
        // Use the public production constructor — it creates its own RestTemplate internally.
        String baseUrl = "http://" + MOCK_SERVER.getHost() + ":" + MOCK_SERVER.getServerPort();
        promotionClient = new PromotionClient(baseUrl);
    }

    @AfterAll
    static void stopContainer() {
        if (MOCK_SERVER.isRunning()) {
            MOCK_SERVER.stop();
        }
    }

    @BeforeEach
    void resetExpectations() {
        mockServerClient.reset();
    }

    // ── getHealthStats ────────────────────────────────────────────────────────

    @Test
    void getHealthStats_200response_returnsDeserializedMap() {
        mockServerClient
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/api/v1/health-status/stats"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"active\":150,\"confirmed\":3,\"suspect\":8,\"probable\":2}"));

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result, "Result must not be null on a 200 response");
        assertFalse(result.containsKey("error"),
                "Happy-path response from promotion-service must not contain an 'error' key");
    }

    @Test
    void getHealthStats_emptyBody_returnsEmptyMap() {
        mockServerClient
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/api/v1/health-status/stats"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{}"));

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Promotion-service returning {} must yield an empty map");
    }

    // ── getHealthStatsByDepartment ────────────────────────────────────────────

    @Test
    void getHealthStatsByDepartment_200response_returnsDeserializedMap() {
        mockServerClient
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/api/v1/health-status/stats/department/Engineering"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"department\":\"Engineering\",\"totalUsers\":45,"
                                + "\"activeCount\":40,\"suspectCount\":3}"));

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("Engineering");

        assertNotNull(result);
        assertFalse(result.containsKey("error"),
                "Department stats happy-path must not return an error key");
        assertEquals("Engineering", result.get("department"));
    }

    @Test
    void getHealthStatsByDepartment_routesCorrectDepartmentInUrl() {
        mockServerClient
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/api/v1/health-status/stats/department/Medicine"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"department\":\"Medicine\",\"totalUsers\":20}"));

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("Medicine");

        assertNotNull(result);
        assertEquals("Medicine", result.get("department"),
                "Client must route the department name in the URL path");
    }
}
