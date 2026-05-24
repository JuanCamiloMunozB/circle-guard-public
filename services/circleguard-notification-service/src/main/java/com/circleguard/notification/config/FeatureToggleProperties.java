package com.circleguard.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "feature")
public class FeatureToggleProperties {

    private SmsAlerts smsAlerts = new SmsAlerts();

    public SmsAlerts getSmsAlerts() {
        return smsAlerts;
    }

    public void setSmsAlerts(SmsAlerts smsAlerts) {
        this.smsAlerts = smsAlerts;
    }

    public static class SmsAlerts {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}