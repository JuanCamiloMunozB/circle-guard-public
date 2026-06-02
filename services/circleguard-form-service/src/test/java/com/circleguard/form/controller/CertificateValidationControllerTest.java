package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CertificateValidationController.class)
class CertificateValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthSurveyService surveyService;

    @Test
    void getPending_returns200WithList() throws Exception {
        when(surveyService.getPendingSurveys()).thenReturn(List.of(
                HealthSurvey.builder().id(UUID.randomUUID()).build()
        ));

        mockMvc.perform(get("/api/v1/certificates/pending"))
                .andExpect(status().isOk());

        verify(surveyService).getPendingSurveys();
    }

    @Test
    void validate_delegatesAllParamsToService() throws Exception {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", surveyId)
                        .param("status", "APPROVED")
                        .param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);
    }

    @Test
    void validate_propagatesRejectedStatus() throws Exception {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", surveyId)
                        .param("status", "REJECTED")
                        .param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(eq(surveyId), eq(ValidationStatus.REJECTED), any());
    }
}
