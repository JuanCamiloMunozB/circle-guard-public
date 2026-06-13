package com.circleguard.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for file-service upload and retrieval flow (E2E Tests 20-23).
 *
 * Targets:
 *   POST /api/v1/files/upload  — multipart file upload
 *   GET  /api/v1/files/{id}    — file metadata retrieval
 *   GET  /actuator/health      — liveness check
 *
 * Requires a fully-deployed stack (AKS stage or local docker-compose).
 * Run via: ./gradlew :tests:e2e:e2eTest
 */
@Tag("e2e")
class FileServiceFlowE2ETest extends BaseE2ETest {

    // ── E2E Test 20 ──────────────────────────────────────────────────────────

    @Test
    void actuatorHealth_fileService_isUp() {
        given()
                .baseUri(BASE_URL).port(FILE_PORT)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    // ── E2E Test 21 ──────────────────────────────────────────────────────────

    @Test
    void uploadFile_validPdf_returns2xx() {
        byte[] pdfContent = "%PDF-1.4 minimal".getBytes();

        fileService()
                .contentType("multipart/form-data")
                .multiPart("file", "certificate.pdf", pdfContent, "application/pdf")
                .when()
                .post("/api/v1/files/upload")
                .then()
                .statusCode(anyOf(is(200), is(201), is(202)));
    }

    // ── E2E Test 22 ──────────────────────────────────────────────────────────

    @Test
    void uploadFile_missingFile_returns4xx() {
        fileService()
                .contentType("multipart/form-data")
                .when()
                .post("/api/v1/files/upload")
                .then()
                .statusCode(anyOf(is(400), is(422)));
    }

    // ── E2E Test 23 ──────────────────────────────────────────────────────────

    @Test
    void getFile_unknownId_returns404() {
        fileService()
                .when()
                .get("/api/v1/files/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(anyOf(is(404), is(400)));
    }
}
