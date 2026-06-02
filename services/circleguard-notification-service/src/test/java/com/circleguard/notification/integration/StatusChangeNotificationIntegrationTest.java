package com.circleguard.notification.integration;

import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test: publishes a promotion.status.changed event to an in-process
 * embedded Kafka broker and verifies that the real ExposureNotificationListener
 * (a @KafkaListener bean) consumes it and delegates to NotificationDispatcher
 * and LmsService.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        // linger.ms=0: send immediately, never batch. Without this the producer may
        // buffer the message for up to the default linger period, causing the consumer
        // to be checked before the message has reached the broker.
        "spring.kafka.producer.properties.linger.ms=0",
        // Fast offset commits: consumer position is persisted quickly between tests so
        // a message consumed in test N is not re-delivered to test N+1.
        "spring.kafka.consumer.enable-auto-commit=true",
        "spring.kafka.consumer.properties.auto.commit.interval.ms=100",
        // application-test.yml sets auto-startup=false to prevent listeners from
        // connecting during unit tests (no broker available there). Integration tests
        // use an embedded broker, so listeners MUST start; override it here.
        "spring.kafka.listener.auto-startup=true"
})
// All topics that have @KafkaListener beans in this service must be listed here.
// waitForListenerAssignment() iterates over EVERY listener container; if any
// subscribed topic is missing, Kafka auto-creates it but the partition assignment
// race causes ContainerTestUtils.waitForAssignment() to time out, failing all tests.
@EmbeddedKafka(partitions = 1, topics = {
        "promotion.status.changed",   // ExposureNotificationListener
        "circle.fenced",              // CircleFencedListener
        "alert.priority"              // PriorityAlertListener
})
@ActiveProfiles("test")
@Tag("integration")
// Explicit order: SUSPECT test first so that tests 2 and 3 (which check never())
// don't see an uncommitted message from test 1 arriving late via the consumer.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusChangeNotificationIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @MockitoBean
    private NotificationDispatcher dispatcher;

    @MockitoBean
    private LmsService lmsService;

    @MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockitoBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @MockitoBean
    private com.circleguard.notification.service.EmailService emailService;

    @MockitoBean
    private com.circleguard.notification.service.SmsService smsService;

    @MockitoBean
    private com.circleguard.notification.service.PushService pushService;

    // Spring's MockitoTestExecutionListener resets @MockitoBean stubs automatically,
    // but Kafka may deliver a lingering message from the previous test AFTER the
    // reset fires. Explicit reset here acts as a second safety net.
    @AfterEach
    void resetMockInteractions() {
        Mockito.reset(dispatcher, lmsService);
    }

    private void waitForListenerAssignment() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
        }
    }

    // Integration Test 8: SUSPECT status event dispatches notification and syncs LMS.
    // Runs first (@Order 1) so that tests 2 and 3 don't see this message via a stale offset.
    @Test
    @Order(1)
    void handleStatusChange_suspectStatus_shouldDispatchAndSyncLms() throws Exception {
        waitForListenerAssignment();

        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        String event = "{\"anonymousId\":\"user-int-001\",\"status\":\"SUSPECT\",\"timestamp\":1234567890}";
        // Block until the broker acks the message. Without .get() the send is async and
        // the consumer may be verified before the message reaches the broker.
        kafkaTemplate.send("promotion.status.changed", "user-int-001", event)
                     .get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            verify(dispatcher).dispatch(eq("user-int-001"), eq("SUSPECT"));
            verify(lmsService).syncRemoteAttendance(eq("user-int-001"), eq("SUSPECT"));
        });
    }

    // Integration Test 9: ACTIVE status event must NOT trigger dispatch (not a risk state).
    @Test
    @Order(2)
    void handleStatusChange_activeStatus_shouldNotDispatch() throws Exception {
        waitForListenerAssignment();

        String event = "{\"anonymousId\":\"user-int-002\",\"status\":\"ACTIVE\",\"timestamp\":1234567890}";
        kafkaTemplate.send("promotion.status.changed", "user-int-002", event)
                     .get(5, TimeUnit.SECONDS);

        // pollDelay gives the consumer time to process the message before we assert never().
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            verify(dispatcher, never()).dispatch(anyString(), anyString());
            verify(lmsService, never()).syncRemoteAttendance(anyString(), anyString());
        });
    }

    // Integration Test 10: malformed JSON must be swallowed by the listener; dispatcher untouched.
    // The listener still consumes the record (deserializing as String never fails); failure happens
    // when ObjectMapper tries to parse it, where the listener's try/catch absorbs it.
    @Test
    @Order(3)
    void handleStatusChange_malformedJson_shouldNotThrow() throws Exception {
        waitForListenerAssignment();

        String badEvent = "THIS IS NOT JSON {{{}}}";
        kafkaTemplate.send("promotion.status.changed", "bad", badEvent)
                     .get(5, TimeUnit.SECONDS);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                verify(dispatcher, never()).dispatch(anyString(), anyString())
        );
    }
}
