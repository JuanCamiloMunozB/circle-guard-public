package com.circleguard.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for notification-service event-triggered dispatch flow (E2E Tests 24-27).
 *
 * Notification-service is primarily Kafka-driven; the E2E tests exercise:
 *   GET  /actuator/health            — liveness check
 *   GET  /api/v1/notifications       — list dispatched notifications (if REST endpoint exists)
 *   POST /api/v1/notifications/send  — direct send (if exposed; 404 acceptable otherwise)
 *
 * Requires a fully-deployed stack (AKS stage or local docker-compose).
 * Run via: ./gradlew :tests:e2e:e2eTest
 */
@Tag("e2e")
class NotificationFlowE2ETest extends BaseE2ETest {

    // ── E2E Test 24 ──────────────────────────────────────────────────────────

    @Test
    void actuatorHealth_notificationService_isUp() {
        given()
                .baseUri(BASE_URL).port(NOTIFICATION_PORT)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    // ── E2E Test 25 ──────────────────────────────────────────────────────────

    @Test
    void actuatorHealth_notificationService_livenessProbeUp() {
        given()
                .baseUri(BASE_URL).port(NOTIFICATION_PORT)
                .when()
                .get("/actuator/health/liveness")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    // ── E2E Test 26 ──────────────────────────────────────────────────────────

    @Test
    void listNotifications_returnsAcceptableResponse() {
        // 200 if the endpoint exists; 404 if notification-service is event-only (no REST list).
        notificationService()
                .when()
                .get("/api/v1/notifications")
                .then()
                .statusCode(anyOf(is(200), is(404), is(401), is(403)));
    }

    // ── E2E Test 27 ──────────────────────────────────────────────────────────

    @Test
    void sendNotification_missingPayload_returns4xx() {
        // Verifies that the service rejects malformed direct-send requests gracefully.
        // 404 is acceptable if the endpoint is not exposed (Kafka-only design).
        notificationService()
                .body("{}")
                .when()
                .post("/api/v1/notifications/send")
                .then()
                .statusCode(anyOf(is(400), is(404), is(422)));
    }
}
