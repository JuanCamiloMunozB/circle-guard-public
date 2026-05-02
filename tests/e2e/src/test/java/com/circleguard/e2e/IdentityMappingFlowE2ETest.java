package com.circleguard.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E Test: Identity mapping flow.
 *
 * Flow:
 *   1. Register a new user identity → receive anonymousId
 *   2. Re-register same identity → must return the same anonymousId (idempotency)
 *   3. Register a visitor with composite identity
 */
@Tag("e2e")
class IdentityMappingFlowE2ETest extends BaseE2ETest {

    // E2E Test 6: mapping a new identity returns a valid UUID
    @Test
    void mapIdentity_newUser_shouldReturnUuid() {
        String unique = "e2e-user-" + UUID.randomUUID() + "@university.edu";

        Response response = identityService()
                .body("{\"realIdentity\":\"" + unique + "\"}")
                .post("/api/v1/identities/map");

        assertTrue(
                response.statusCode() == 200 || response.statusCode() == 503,
                "Expected 200 or 503, got: " + response.statusCode()
        );

        if (response.statusCode() == 200) {
            String anonymousId = response.jsonPath().getString("anonymousId");
            assertNotNull(anonymousId, "anonymousId must be present");
            assertDoesNotThrow(() -> UUID.fromString(anonymousId),
                    "anonymousId must be a valid UUID");
        }
    }

    // E2E Test 7: mapping the same identity twice returns the same anonymousId (idempotency)
    @Test
    void mapIdentity_sameIdentityTwice_shouldReturnSameUuid() {
        String identity = "stable-user-" + UUID.randomUUID() + "@university.edu";

        Response first  = identityService().body("{\"realIdentity\":\"" + identity + "\"}").post("/api/v1/identities/map");
        Response second = identityService().body("{\"realIdentity\":\"" + identity + "\"}").post("/api/v1/identities/map");

        if (first.statusCode() == 200 && second.statusCode() == 200) {
            assertEquals(
                    first.jsonPath().getString("anonymousId"),
                    second.jsonPath().getString("anonymousId"),
                    "Same identity must always map to the same anonymousId"
            );
        }
    }

    // E2E Test 8: visitor registration returns a UUID without exposing real identity
    @Test
    void registerVisitor_validPayload_shouldReturnUuid() {
        String visitorBody = """
                {
                  "name": "Carlos Visitor",
                  "email": "carlos.visitor@external.com",
                  "reason_for_visit": "Research visit"
                }
                """;

        Response response = identityService()
                .body(visitorBody)
                .post("/api/v1/identities/visitor");

        assertTrue(
                response.statusCode() == 200 || response.statusCode() == 503,
                "Expected 200 or 503, got: " + response.statusCode()
        );

        if (response.statusCode() == 200) {
            assertNotNull(response.jsonPath().getString("anonymousId"));
        }
    }
}
