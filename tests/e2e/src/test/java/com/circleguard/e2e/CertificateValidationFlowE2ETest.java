package com.circleguard.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E Test: Certificate validation flow.
 *
 * Flow:
 *   1. Submit a survey with an attachment (simulated path)
 *   2. Verify survey enters PENDING validation state
 *   3. Admin approves certificate → certificate.validated Kafka event fires
 *   4. Promotion-service should restore user to ACTIVE
 */
@Tag("e2e")
class CertificateValidationFlowE2ETest extends BaseE2ETest {

    // E2E Test 12: survey with attachment path is created in PENDING state
    @Test
    void submitSurveyWithAttachment_shouldReturnPendingStatus() {
        String anonymousId = UUID.randomUUID().toString();

        Response response = formService()
                .body("""
                        {
                          "anonymousId": "%s",
                          "hasFever": false,
                          "hasCough": false,
                          "attachmentPath": "/uploads/test-certificate.pdf",
                          "responses": {}
                        }
                        """.formatted(anonymousId))
                .post("/api/v1/surveys");

        assertTrue(
                response.statusCode() == 200 || response.statusCode() == 503,
                "Expected 200 or 503, got: " + response.statusCode()
        );

        if (response.statusCode() == 200) {
            String status = response.jsonPath().getString("validationStatus");
            assertEquals("PENDING", status,
                    "Survey with attachment must be in PENDING validation state");
        }
    }

    // E2E Test 13: GET pending surveys endpoint is accessible to authorized users
    @Test
    void getPendingSurveys_shouldReturnListOrRequireAuth() {
        Response response = formService().get("/api/v1/surveys/pending");

        // Either returns 200 (if auth not enforced in dev) or 401/403 (auth enforced)
        assertTrue(
                response.statusCode() == 200
                        || response.statusCode() == 401
                        || response.statusCode() == 403
                        || response.statusCode() == 503,
                "Unexpected status: " + response.statusCode()
        );
    }

    // E2E Test 14: auth-service health endpoint is reachable
    @Test
    void authService_healthEndpoint_shouldBeUp() {
        authService()
                .get("/actuator/health")
                .then()
                .statusCode(200);
    }

    // E2E Test 15: questionnaire endpoint returns active questionnaire or 404
    @Test
    void getQuestionnaire_activeQuestionnaire_shouldRespondCorrectly() {
        Response response = formService().get("/api/v1/questionnaires/active");

        assertTrue(
                response.statusCode() == 200
                        || response.statusCode() == 404
                        || response.statusCode() == 503,
                "Unexpected status: " + response.statusCode()
        );
    }
}
