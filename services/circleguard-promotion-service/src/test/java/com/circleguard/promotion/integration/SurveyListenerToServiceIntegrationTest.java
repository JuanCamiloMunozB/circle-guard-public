package com.circleguard.promotion.integration;

import com.circleguard.promotion.listener.SurveyListener;
import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: verifies that SurveyListener correctly delegates to HealthStatusService
 * within the full Spring context, validating proper bean wiring between listener and service.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class SurveyListenerToServiceIntegrationTest {

    @Autowired
    private SurveyListener surveyListener;

    @MockBean
    private HealthStatusService healthStatusService;

    // Integration Test 3: survey.submitted with symptoms triggers SUSPECT promotion via service
    @Test
    void surveyListener_withSymptoms_shouldCallHealthStatusServiceWithSuspect() {
        Map<String, Object> event = Map.of(
                "anonymousId", "integration-user-001",
                "hasSymptoms", true,
                "timestamp", System.currentTimeMillis()
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService).updateStatus("integration-user-001", "SUSPECT");
    }

    // Integration Test 4: survey.submitted without symptoms should NOT call healthStatusService
    @Test
    void surveyListener_withoutSymptoms_shouldNotCallHealthStatusService() {
        Map<String, Object> event = Map.of(
                "anonymousId", "integration-user-002",
                "hasSymptoms", false,
                "timestamp", System.currentTimeMillis()
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }
}
