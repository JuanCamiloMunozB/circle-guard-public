package com.circleguard.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for gateway-service QR-code validation flow (E2E Tests 16-19).
 *
 * Targets: POST /api/v1/qr/validate
 *
 * Requires a fully-deployed stack (AKS stage or local docker-compose).
 * Run via: ./gradlew :tests:e2e:e2eTest
 */
@Tag("e2e")
class GatewayFlowE2ETest extends BaseE2ETest {

    // ── E2E Test 16 ──────────────────────────────────────────────────────────

    @Test
    void validate_missingToken_returns400() {
        gatewayService()
                .body("{}")
                .when()
                .post("/api/v1/qr/validate")
                .then()
                .statusCode(anyOf(is(400), is(422)));
    }

    // ── E2E Test 17 ──────────────────────────────────────────────────────────

    @Test
    void validate_malformedToken_returnsRedStatus() {
        gatewayService()
                .body("{\"token\": \"not.a.valid.jwt\"}")
                .when()
                .post("/api/v1/qr/validate")
                .then()
                .statusCode(anyOf(is(200), is(401)))
                .body("status", anyOf(equalTo("RED"), nullValue()));
    }

    // ── E2E Test 18 ──────────────────────────────────────────────────────────

    @Test
    void actuatorHealth_gatewayService_isUp() {
        given()
                .baseUri(BASE_URL).port(GATEWAY_PORT)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    // ── E2E Test 19 ──────────────────────────────────────────────────────────

    @Test
    void validate_emptyToken_returns4xx() {
        gatewayService()
                .body("{\"token\": \"\"}")
                .when()
                .post("/api/v1/qr/validate")
                .then()
                .statusCode(anyOf(is(400), is(401), is(422)));
    }
}
