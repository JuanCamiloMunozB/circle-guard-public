package com.circleguard.form.controller;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.QuestionnaireService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestionnaireController.class)
class QuestionnaireControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionnaireService questionnaireService;

    @Test
    void shouldReturnActiveQuestionnaire() throws Exception {
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("Daily Health Check")
                .isActive(true)
                .version(1)
                .build();

        Mockito.when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(q));

        mockMvc.perform(get("/api/v1/questionnaires/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily Health Check"));
    }

    @Test
    void shouldCreateQuestionnaire() throws Exception {
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("New Survey")
                .version(1)
                .build();

        Mockito.when(questionnaireService.saveQuestionnaire(Mockito.any(Questionnaire.class))).thenReturn(q);

        mockMvc.perform(post("/api/v1/questionnaires")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"New Survey\", \"version\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Survey"));
    }

    @Test
    void shouldCreateQuestionnaireWithQuestions() throws Exception {
        Questionnaire saved = Questionnaire.builder().id(UUID.randomUUID()).title("With Qs").build();
        Mockito.when(questionnaireService.saveQuestionnaire(Mockito.any(Questionnaire.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/questionnaires")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"With Qs\", \"version\": 2, \"isActive\": true, \"questions\": ["
                        + "{\"text\": \"Do you have a fever?\", \"options\": \"yes,no\", \"orderIndex\": 0}]}"))
                .andExpect(status().isOk());

        // Verify the controller mapped the request questions onto fresh entities
        // and wired the back-reference (exercises the questions != null branch).
        ArgumentCaptor<Questionnaire> captor = ArgumentCaptor.forClass(Questionnaire.class);
        Mockito.verify(questionnaireService).saveQuestionnaire(captor.capture());
        Questionnaire built = captor.getValue();
        assertEquals("With Qs", built.getTitle());
        assertNotNull(built.getQuestions());
        assertEquals(1, built.getQuestions().size());
        assertEquals("Do you have a fever?", built.getQuestions().get(0).getText());
        assertSame(built, built.getQuestions().get(0).getQuestionnaire(),
                "each Question must back-reference its owning Questionnaire");
    }

    @Test
    void shouldReturnAllQuestionnaires() throws Exception {
        Mockito.when(questionnaireService.getAllQuestionnaires())
                .thenReturn(List.of(Questionnaire.builder().id(UUID.randomUUID()).title("Q1").build()));

        mockMvc.perform(get("/api/v1/questionnaires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Q1"));
    }

    @Test
    void shouldReturnNotFoundWhenNoActiveQuestionnaire() throws Exception {
        Mockito.when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/questionnaires/active"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldActivateQuestionnaire() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/questionnaires/" + id + "/activate"))
                .andExpect(status().isOk());

        Mockito.verify(questionnaireService).activateQuestionnaire(id);
    }
}
