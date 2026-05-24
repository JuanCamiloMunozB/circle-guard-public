package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(kafkaTemplate);
    }

    @Test
    void logDelivery_publishesEnrichedAuditEventToAuditTopic() {
        service.logDelivery("anon-123", "EMAIL", "SUCCESS", "corr-xyz");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("notification.audit"), eq("anon-123"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertEquals("anon-123", payload.get("userId"));
        assertEquals("EMAIL", payload.get("channel"));
        assertEquals("SUCCESS", payload.get("status"));
        assertEquals("corr-xyz", payload.get("correlationId"));
        assertNotNull(payload.get("eventId"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    void logDelivery_generatesCorrelationIdWhenNullProvided() {
        service.logDelivery("anon-456", "SMS", "FAILED", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("notification.audit"), eq("anon-456"), captor.capture());
        Object correlationId = captor.getValue().get("correlationId");
        assertNotNull(correlationId, "fallback correlationId must be generated when caller passes null");
        // UUID toString length is 36
        assertEquals(36, correlationId.toString().length());
    }
}
