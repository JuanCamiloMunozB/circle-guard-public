package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PushServiceImplTest {

    private AuditLogService auditLog;
    private PushServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLog = mock(AuditLogService.class);
        WebClient.Builder builder = WebClient.builder();
        service = new PushServiceImpl(builder, "http://gotify.local");
        ReflectionTestUtils.setField(service, "auditLogService", auditLog);
        ReflectionTestUtils.setField(service, "gotifyToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(service, "gotifyUrl", "http://gotify.local");
    }

    @Test
    void sendAsync_withoutMetadata_routesToMockBranchAndAudits() throws Exception {
        CompletableFuture<Void> future = service.sendAsync("anon-1", "hello push");

        future.get();
        verify(auditLog).logDelivery(eq("anon-1"), eq("PUSH"), eq("SUCCESS"), any());
    }

    @Test
    void sendAsync_withMetadata_mockBranchStillSuccessful() throws Exception {
        CompletableFuture<Void> future = service.sendAsync("anon-2", "msg",
                Map.of("priority", "high", "deeplink", "/alerts"));

        future.get();
        verify(auditLog).logDelivery(eq("anon-2"), eq("PUSH"), eq("SUCCESS"), any());
    }

    @Test
    void recover_logsFailedAndReturnsFailedFuture() {
        CompletableFuture<Void> recovered = service.recover(
                new RuntimeException("gotify down"), "anon-3", "msg", Map.of());

        assertTrue(recovered.isCompletedExceptionally());
        verify(auditLog).logDelivery(eq("anon-3"), eq("PUSH"), eq("FAILED"), eq(null));
    }
}
