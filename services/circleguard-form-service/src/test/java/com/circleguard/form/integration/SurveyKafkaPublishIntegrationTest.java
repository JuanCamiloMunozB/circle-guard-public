package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.HealthSurveyService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test: verifies that HealthSurveyService publishes the expected
 * Kafka events end-to-end against an in-process embedded broker (no external
 * Kafka required).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
})
@EmbeddedKafka(partitions = 1, topics = {"survey.submitted", "certificate.validated"})
@ActiveProfiles("test")
@Tag("integration")
class SurveyKafkaPublishIntegrationTest {

    @Autowired
    private HealthSurveyService surveyService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private com.circleguard.form.repository.HealthSurveyRepository surveyRepository;

    @MockitoBean
    private com.circleguard.form.service.QuestionnaireService questionnaireService;

    @MockitoBean
    private com.circleguard.form.service.SymptomMapper symptomMapper;

    private Consumer<String, Map<String, Object>> testConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(
                "test-group-" + UUID.randomUUID(), "true", embeddedKafka));
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Map.class.getName());
        testConsumer = new DefaultKafkaConsumerFactory<String, Map<String, Object>>(consumerProps).createConsumer();
        embeddedKafka.consumeFromAllEmbeddedTopics(testConsumer);
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) testConsumer.close();
    }

    // Integration Test 1: survey.submitted event payload must include anonymousId and hasSymptoms
    @Test
    void submitSurvey_shouldPublishSurveySubmittedEventWithCorrectPayload() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).build();

        Question feverQ = Question.builder()
                .id(UUID.randomUUID()).text("Do you have a fever?").type(QuestionType.YES_NO).build();
        Questionnaire questionnaire = Questionnaire.builder().questions(List.of(feverQ)).build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(true);
        when(surveyRepository.save(any())).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        surveyService.submitSurvey(survey);

        ConsumerRecord<String, Map<String, Object>> record =
                KafkaTestUtils.getSingleRecord(testConsumer, "survey.submitted", Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(anonymousId.toString());
        Map<String, Object> payload = record.value();
        assertThat(payload).containsKeys("anonymousId", "hasSymptoms", "timestamp");
        assertThat(payload.get("hasSymptoms")).isEqualTo(true);
    }

    // Integration Test 2: certificate.validated event must carry adminId and APPROVED status
    @Test
    void validateSurvey_approved_shouldPublishCertificateEventWithAdminId() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(com.circleguard.form.model.ValidationStatus.PENDING)
                .build();

        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        surveyService.validateSurvey(surveyId, com.circleguard.form.model.ValidationStatus.APPROVED, adminId);

        ConsumerRecord<String, Map<String, Object>> record =
                KafkaTestUtils.getSingleRecord(testConsumer, "certificate.validated", Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(anonymousId.toString());
        Map<String, Object> payload = record.value();
        assertThat(payload.get("status")).isEqualTo("APPROVED");
        assertThat(payload.get("adminId")).isEqualTo(adminId.toString());
    }
}
