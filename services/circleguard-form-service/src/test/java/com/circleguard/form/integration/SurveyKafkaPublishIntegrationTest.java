package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: verifies Form Service wires HealthSurveyService → KafkaTemplate correctly.
 * External dependencies (DB, real Kafka) are mocked; the Spring context is loaded fully to
 * confirm bean wiring and transaction boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class SurveyKafkaPublishIntegrationTest {

    @Autowired
    private HealthSurveyService surveyService;

    @MockBean
    private com.circleguard.form.repository.HealthSurveyRepository surveyRepository;

    @MockBean
    private com.circleguard.form.service.QuestionnaireService questionnaireService;

    @MockBean
    private com.circleguard.form.service.SymptomMapper symptomMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Integration Test 1: survey.submitted event payload must include anonymousId and hasSymptoms
    @Test
    void submitSurvey_shouldPublishSurveySubmittedEventWithCorrectPayload() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).build();

        UUID questionId = UUID.randomUUID();
        Question feverQ = Question.builder()
                .id(questionId).text("Do you have a fever?").type(QuestionType.YES_NO).build();
        Questionnaire questionnaire = Questionnaire.builder().questions(List.of(feverQ)).build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(true);
        when(surveyRepository.save(any())).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        surveyService.submitSurvey(survey);

        verify(kafkaTemplate).send(
                eq("survey.submitted"),
                eq(anonymousId.toString()),
                argThat(payload -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    return map.containsKey("anonymousId")
                            && map.containsKey("hasSymptoms")
                            && map.containsKey("timestamp")
                            && Boolean.TRUE.equals(map.get("hasSymptoms"));
                })
        );
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

        verify(kafkaTemplate).send(
                eq("certificate.validated"),
                eq(anonymousId.toString()),
                argThat(payload -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    return "APPROVED".equals(map.get("status"))
                            && adminId.equals(map.get("adminId"));
                })
        );
    }
}
