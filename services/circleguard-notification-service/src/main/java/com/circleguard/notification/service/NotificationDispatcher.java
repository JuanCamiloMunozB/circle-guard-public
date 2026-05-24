package com.circleguard.notification.service;

import com.circleguard.notification.config.FeatureToggleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final TemplateService templateService;
    private final FeatureToggleProperties featureToggleProperties;

    public void dispatch(String userId, String status) {
        log.info("Dispatching contextual multi-channel notifications for user: {} with status: {}", userId, status);
        
        String emailContent = templateService.generateEmailContent(status, userId);
        String pushContent = templateService.generatePushContent(status);
        Map<String, String> pushMetadata = templateService.generatePushMetadata(status);

        CompletableFuture<Void> emailFuture = emailService.sendAsync(userId, emailContent);
        CompletableFuture<Void> pushFuture = pushService.sendAsync(userId, pushContent, pushMetadata);
        CompletableFuture<Void> smsFuture = CompletableFuture.completedFuture(null);

        if (featureToggleProperties.getSmsAlerts().isEnabled()) {
            String smsContent = templateService.generateSmsContent(status);
            smsFuture = smsService.sendAsync(userId, smsContent);
        } else {
            log.info("SMS alerts disabled by feature flag for user: {}", userId);
        }

        CompletableFuture.allOf(emailFuture, smsFuture, pushFuture).handle((result, ex) -> {
            if (ex != null) {
                log.error("Error during multi-channel dispatch for user {}: {}", userId, ex.getMessage());
            } else {
                log.info("Multi-channel dispatch completed successfully for user: {}", userId);
            }
            return result;
        });
    }
}
