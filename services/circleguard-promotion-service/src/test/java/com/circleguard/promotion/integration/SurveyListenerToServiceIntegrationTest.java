package com.circleguard.promotion.integration;

import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * True integration test: publishes a survey.submitted Kafka event and verifies
 * that the real SurveyListener → HealthStatusService pipeline updates the user
 * node status in embedded Neo4j.
 *
 * No mocks of the service layer — the full execution chain runs against
 * embedded infrastructure (Neo4j harness + jedis-mock + embedded Kafka).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.main.allow-bean-definition-overriding=true",
        // application-test.yml disables auto-startup to prevent listeners from connecting
        // to localhost:9092 in unit tests. Re-enable here since embedded Kafka is running.
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, topics = {
        "survey.submitted",
        "promotion.status.changed",
        "alert.priority",
        "circle.fenced"
})
@ActiveProfiles("test")
@Tag("integration")
class SurveyListenerToServiceIntegrationTest {

    // Static fields initialized at class-load time so @DynamicPropertySource
    // can read them before Spring creates the application context.
    private static final Neo4j EMBEDDED_NEO4J = Neo4jBuilders.newInProcessBuilder()
            .withDisabledServer()
            .build();

    private static final RedisServer REDIS_MOCK = startRedisMock();

    private static RedisServer startRedisMock() {
        try {
            return RedisServer.newRedisServer().start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start Redis mock", e);
        }
    }

    @AfterAll
    static void stopInfra() throws IOException {
        EMBEDDED_NEO4J.close();
        REDIS_MOCK.stop();
    }

    @DynamicPropertySource
    static void overrideInfraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", EMBEDDED_NEO4J::boltURI);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "");
        registry.add("spring.data.redis.host", REDIS_MOCK::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS_MOCK.getBindPort()));
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void waitForListenerAssignment() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
        }
    }

    @AfterEach
    void cleanNeo4j() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    // Integration Test 3: Kafka event with hasSymptoms=true must produce status=SUSPECT in Neo4j
    @Test
    void surveyWithSymptoms_shouldSetUserStatusSuspectInNeo4j() {
        neo4jClient.query(
                "CREATE (:User {anonymousId: 'int-user-001', status: 'ACTIVE', statusUpdatedAt: 0})"
        ).run();

        kafkaTemplate.send("survey.submitted", "int-user-001", Map.of(
                "anonymousId", "int-user-001",
                "hasSymptoms", true,
                "timestamp", System.currentTimeMillis()
        ));
        // send() only buffers the record; under CI load the producer can hold it
        // long enough that the 15s poll window expires before the listener ever sees
        // the message. flush() forces the record onto the broker before we start
        // polling so the await window measures listener+Neo4j latency, not send latency.
        kafkaTemplate.flush();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<Map<String, Object>> row = neo4jClient
                    .query("MATCH (u:User {anonymousId: 'int-user-001'}) RETURN u.status AS status")
                    .fetch().one();
            assertTrue(row.isPresent(), "User node not found in Neo4j");
            assertEquals("SUSPECT", row.get().get("status"),
                    "Status should be SUSPECT after survey with symptoms");
        });
    }

    // Integration Test 4: Kafka event with hasSymptoms=false must leave Neo4j status unchanged
    @Test
    void surveyWithoutSymptoms_shouldLeaveStatusActiveInNeo4j() {
        neo4jClient.query(
                "CREATE (:User {anonymousId: 'int-user-002', status: 'ACTIVE', statusUpdatedAt: 0})"
        ).run();

        kafkaTemplate.send("survey.submitted", "int-user-002", Map.of(
                "anonymousId", "int-user-002",
                "hasSymptoms", false,
                "timestamp", System.currentTimeMillis()
        ));
        // Force delivery before the (deliberately short) no-change window so this test
        // verifies the listener actually processed a no-symptoms event and left the
        // status untouched — rather than passing only because the message was still
        // sitting unsent in the producer buffer.
        kafkaTemplate.flush();

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            Optional<Map<String, Object>> row = neo4jClient
                    .query("MATCH (u:User {anonymousId: 'int-user-002'}) RETURN u.status AS status")
                    .fetch().one();
            assertTrue(row.isPresent(), "User node not found in Neo4j");
            assertEquals("ACTIVE", row.get().get("status"),
                    "Status must remain ACTIVE when survey has no symptoms");
        });
    }
}
