package com.circleguard.promotion.integration;

import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration test: publishes a survey.submitted event to an in-process embedded
 * Kafka broker and verifies that SurveyListener (the real @KafkaListener bean)
 * consumes it and delegates to HealthStatusService.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.main.allow-bean-definition-overriding=true"
})
@EmbeddedKafka(partitions = 1, topics = {"survey.submitted"})
@ActiveProfiles("test")
@Tag("integration")
class SurveyListenerToServiceIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @MockBean
    private HealthStatusService healthStatusService;

    private void waitForListenerAssignment() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
        }
    }

    // Integration Test 3: survey.submitted with symptoms triggers SUSPECT promotion via service
    @Test
    void surveyListener_withSymptoms_shouldCallHealthStatusServiceWithSuspect() {
        waitForListenerAssignment();

        Map<String, Object> event = Map.of(
                "anonymousId", "integration-user-001",
                "hasSymptoms", true,
                "timestamp", System.currentTimeMillis()
        );

        kafkaTemplate.send("survey.submitted", "integration-user-001", event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(healthStatusService).updateStatus("integration-user-001", "SUSPECT")
        );
    }

    // Integration Test 4: survey.submitted without symptoms should NOT trigger any status update
    @Test
    void surveyListener_withoutSymptoms_shouldNotCallHealthStatusService() {
        waitForListenerAssignment();

        Map<String, Object> event = Map.of(
                "anonymousId", "integration-user-002",
                "hasSymptoms", false,
                "timestamp", System.currentTimeMillis()
        );

        kafkaTemplate.send("survey.submitted", "integration-user-002", event);

        // Give the listener a moment to consume; assert the service was never called.
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(healthStatusService, never()).updateStatus(anyString(), anyString())
        );
    }
}
