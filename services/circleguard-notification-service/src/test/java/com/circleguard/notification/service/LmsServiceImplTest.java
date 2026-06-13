package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LmsServiceImplTest {

    private LmsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LmsServiceImpl();
        ReflectionTestUtils.setField(service, "lmsApiUrl", "https://lms.test/api/v1");
        ReflectionTestUtils.setField(service, "identityApiUrl", "http://identity.test:8083");
    }

    @Test
    void syncRemoteAttendance_completesSuccessfullyAndReturnsNullPayload() throws Exception {
        // Long-enough anonymousId so the mock resolution can substring(0, 8)
        String anonymousId = "00000000-aaaa-bbbb-cccc-111111111111";

        CompletableFuture<Void> future = service.syncRemoteAttendance(anonymousId, "PROBABLE");

        assertTrue(future.isDone());
        assertNull(future.get(), "stubbed LMS sync must complete with null payload");
    }

    @Test
    void syncRemoteAttendance_acceptsAnyStatusString() {
        String anonymousId = "abcdef12-3456-7890-1234-567890abcdef";

        // Different statuses must all be accepted; the current impl just logs them.
        // CompletableFuture<Void> resolves with null, so we assert on the future state.
        CompletableFuture<Void> active = service.syncRemoteAttendance(anonymousId, "ACTIVE");
        assertNotNull(active);
        assertTrue(active.isDone());

        CompletableFuture<Void> confirmed = service.syncRemoteAttendance(anonymousId, "CONFIRMED");
        assertNotNull(confirmed);
        assertTrue(confirmed.isDone());
    }
}
