package com.circleguard.dashboard.integration;

import com.circleguard.dashboard.client.PromotionClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the dashboard-service → promotion-service REST link.
 *
 * Uses WireMock (in-process, no Docker) to emulate promotion-service's
 * health-status endpoints. PromotionClient is wired directly against the
 * WireMock server URL so that the full HTTP round-trip — including JSON
 * deserialization — is exercised without any external infrastructure.
 *
 * Circuit-breaker fallback behaviour (AOP-dependent) is covered separately
 * by PromotionClientCircuitBreakerTest (unit test). This test focuses on
 * the happy-path contract between both services.
 *
 * Tagged @Tag("integration") so it only runs under
 * {@code ./gradlew integrationTest}, never as part of the regular {@code test} task.
 */
@Tag("integration")
class PromotionClientIntegrationTest {

    private static WireMockServer wireMock;
    private static PromotionClient promotionClient;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        promotionClient = new PromotionClient("http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    // ── getHealthStats ────────────────────────────────────────────────────────

    @Test
    void getHealthStats_200response_returnsDeserializedMap() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"active\":150,\"confirmed\":3,\"suspect\":8,\"probable\":2}")));

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result, "Result must not be null on a 200 response");
        assertFalse(result.containsKey("error"),
                "Happy-path response from promotion-service must not contain an 'error' key");
    }

    @Test
    void getHealthStats_emptyBody_returnsEmptyMap() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Promotion-service returning {} must yield an empty map");
    }

    // ── getHealthStatsByDepartment ────────────────────────────────────────────

    @Test
    void getHealthStatsByDepartment_200response_returnsDeserializedMap() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/health-status/stats/department/Engineering"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"department\":\"Engineering\",\"totalUsers\":45,"
                                + "\"activeCount\":40,\"suspectCount\":3}")));

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("Engineering");

        assertNotNull(result);
        assertFalse(result.containsKey("error"),
                "Department stats happy-path must not return an error key");
        assertEquals("Engineering", result.get("department"));
    }

    @Test
    void getHealthStatsByDepartment_routesCorrectDepartmentInUrl() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/health-status/stats/department/Medicine"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"department\":\"Medicine\",\"totalUsers\":20}")));

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("Medicine");

        assertNotNull(result);
        assertEquals("Medicine", result.get("department"),
                "Client must route the department name in the URL path");
    }
}
