package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthSurveyServiceTest {

    @Mock
    private HealthSurveyRepository repository;

    @Mock
    private QuestionnaireService questionnaireService;

    @Mock
    private SymptomMapper symptomMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private HealthSurveyService service;

    @BeforeEach
    void setUp() {
        service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);
    }

    // --- Unit Test 1: survey without symptoms should save and publish Kafka event ---
    @Test
    void submitSurvey_withoutSymptoms_shouldSaveAndPublishEvent() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .build();

        Questionnaire questionnaire = Questionnaire.builder().questions(List.of()).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        HealthSurvey result = service.submitSurvey(survey);

        assertNotNull(result.getId());
        assertFalse(result.getHasFever());
        assertFalse(result.getHasCough());
        verify(kafkaTemplate).send(eq("survey.submitted"), anyString(), any(Map.class));
    }

    // --- Unit Test 2: survey with symptoms should flag hasFever and hasCough ---
    @Test
    void submitSurvey_withSymptoms_shouldFlagFields() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .build();

        Questionnaire questionnaire = Questionnaire.builder().questions(List.of()).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        HealthSurvey result = service.submitSurvey(survey);

        assertTrue(result.getHasFever());
        assertTrue(result.getHasCough());
    }

    // --- Unit Test 3: survey with attachment should get PENDING validation status ---
    @Test
    void submitSurvey_withAttachment_shouldSetPendingStatus() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .attachmentPath("/uploads/evidence.pdf")
                .build();

        // When questionnaire is absent, symptomMapper.hasSymptoms() is never called
        // (the Optional.map() is skipped and orElse(false) is returned directly)
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitSurvey(survey);

        ArgumentCaptor<HealthSurvey> captor = ArgumentCaptor.forClass(HealthSurvey.class);
        verify(repository).save(captor.capture());
        assertEquals(ValidationStatus.PENDING, captor.getValue().getValidationStatus());
    }

    // --- Unit Test 4: validateSurvey APPROVED should publish certificate.validated event ---
    @Test
    void validateSurvey_approved_shouldPublishCertificateEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        verify(kafkaTemplate).send(eq("certificate.validated"), anyString(), any(Map.class));
    }

    // --- Unit Test 5: validateSurvey REJECTED should NOT publish certificate.validated event ---
    @Test
    void validateSurvey_rejected_shouldNotPublishCertificateEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(UUID.randomUUID())
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);

        verify(kafkaTemplate, never()).send(eq("certificate.validated"), anyString(), any());
    }
}
