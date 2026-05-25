package com.circleguard.notification.service;

import com.circleguard.notification.config.FeatureToggleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDispatcherToggleTest {

    private EmailService emailService;
    private SmsService smsService;
    private PushService pushService;
    private TemplateService templateService;
    private FeatureToggleProperties featureToggleProperties;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        smsService = mock(SmsService.class);
        pushService = mock(PushService.class);
        templateService = mock(TemplateService.class);
        featureToggleProperties = new FeatureToggleProperties();

        dispatcher = new NotificationDispatcher(
                emailService,
                smsService,
                pushService,
                templateService,
                featureToggleProperties
        );

        when(emailService.sendAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(templateService.generateEmailContent(anyString(), anyString())).thenReturn("email-body");
        when(templateService.generatePushContent(anyString())).thenReturn("push-body");
        when(templateService.generatePushMetadata(anyString())).thenReturn(Map.of("kind", "push"));
        when(templateService.generateSmsContent(anyString())).thenReturn("sms-body");
    }

    @Test
    void dispatchSkipsSmsWhenToggleIsDisabled() {
        featureToggleProperties.getSmsAlerts().setEnabled(false);

        dispatcher.dispatch("user-123", "SUSPECT");

        verify(emailService).sendAsync(eq("user-123"), eq("email-body"));
        verify(pushService).sendAsync(eq("user-123"), eq("push-body"), eq(Map.of("kind", "push")));
        verify(smsService, never()).sendAsync(anyString(), anyString());
        verify(templateService, never()).generateSmsContent(anyString());
    }

    @Test
    void dispatchSendsSmsWhenToggleIsEnabled() {
        featureToggleProperties.getSmsAlerts().setEnabled(true);

        dispatcher.dispatch("user-456", "PROBABLE");

        verify(emailService).sendAsync(eq("user-456"), eq("email-body"));
        verify(pushService).sendAsync(eq("user-456"), eq("push-body"), eq(Map.of("kind", "push")));
        verify(smsService).sendAsync(eq("user-456"), eq("sms-body"));
        verify(templateService).generateSmsContent(eq("PROBABLE"));
    }
}