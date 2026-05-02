package com.circleguard.notification.integration;

import com.circleguard.notification.service.ExposureNotificationListener;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: verifies that ExposureNotificationListener correctly delegates
 * to NotificationDispatcher and LmsService when a promotion.status.changed event arrives.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class StatusChangeNotificationIntegrationTest {

    @Autowired
    private ExposureNotificationListener listener;

    @MockBean
    private NotificationDispatcher dispatcher;

    @MockBean
    private LmsService lmsService;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @MockBean
    private com.circleguard.notification.service.EmailService emailService;

    @MockBean
    private com.circleguard.notification.service.SmsService smsService;

    @MockBean
    private com.circleguard.notification.service.PushService pushService;

    // Integration Test 8: SUSPECT status event dispatches notification and syncs LMS
    @Test
    void handleStatusChange_suspectStatus_shouldDispatchAndSyncLms() throws Exception {
        String event = "{\"anonymousId\":\"user-int-001\",\"status\":\"SUSPECT\",\"timestamp\":1234567890}";

        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        listener.handleStatusChange(event);

        verify(dispatcher).dispatch(eq("user-int-001"), eq("SUSPECT"));
        verify(lmsService).syncRemoteAttendance(eq("user-int-001"), eq("SUSPECT"));
    }

    // Integration Test 9: ACTIVE status event must NOT trigger dispatch (not a risk state)
    @Test
    void handleStatusChange_activeStatus_shouldNotDispatch() throws Exception {
        String event = "{\"anonymousId\":\"user-int-002\",\"status\":\"ACTIVE\",\"timestamp\":1234567890}";

        listener.handleStatusChange(event);

        verify(dispatcher, never()).dispatch(anyString(), anyString());
        verify(lmsService, never()).syncRemoteAttendance(anyString(), anyString());
    }

    // Integration Test 10: malformed JSON should not throw and should not call dispatcher
    @Test
    void handleStatusChange_malformedJson_shouldNotThrow() {
        String badEvent = "THIS IS NOT JSON {{{}}}";

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> listener.handleStatusChange(badEvent)
        );
        verify(dispatcher, never()).dispatch(anyString(), anyString());
    }
}
