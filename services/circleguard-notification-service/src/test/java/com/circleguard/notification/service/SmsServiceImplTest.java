package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SmsServiceImplTest {

    private AuditLogService auditLog;
    private SmsServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLog = mock(AuditLogService.class);
        service = new SmsServiceImpl();
        // Mock SID activates the MOCK branch so we don't touch real Twilio.
        ReflectionTestUtils.setField(service, "accountSid", "AC_MOCK_SID");
        ReflectionTestUtils.setField(service, "authToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(service, "fromNumber", "+15550000000");
        ReflectionTestUtils.setField(service, "auditLogService", auditLog);
    }

    @Test
    void init_mockSid_skipsTwilioInitialization() {
        // The init() method must NOT call Twilio.init for MOCK accountSid.
        // We invoke it directly; if it tried to authenticate, this would throw.
        assertDoesNotThrow(() -> service.init());
    }

    @Test
    void sendAsync_mockBranch_returnsImmediatelyAndLogsSuccess() throws Exception {
        CompletableFuture<Void> future = service.sendAsync("anon-1", "stay home");

        future.get();
        verify(auditLog).logDelivery(eq("anon-1"), eq("SMS"), eq("SUCCESS"), any());
    }

    @Test
    void recover_logsFailedAndReturnsFailedFuture() {
        CompletableFuture<Void> recovered = service.recover(
                new RuntimeException("twilio down"), "anon-9", "msg");

        assertTrue(recovered.isCompletedExceptionally());
        verify(auditLog).logDelivery(eq("anon-9"), eq("SMS"), eq("FAILED"), eq(null));
    }
}
