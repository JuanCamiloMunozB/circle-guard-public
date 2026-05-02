package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SymptomMapperEdgeCasesTest {

    private final SymptomMapper mapper = new SymptomMapper();

    // --- Unit Test 6: null responses should return false ---
    @Test
    void hasSymptoms_nullResponses_shouldReturnFalse() {
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of())
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(null)
                .build();

        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    // --- Unit Test 7: breathing difficulty should be detected as symptom ---
    @Test
    void hasSymptoms_breathingDifficulty_shouldReturnTrue() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have difficulty breathing?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }

    // --- Unit Test 8: unrelated YES answer should not trigger symptom flag ---
    @Test
    void hasSymptoms_unrelatedYesAnswer_shouldReturnFalse() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Have you been vaccinated?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    // --- Unit Test 9: empty questionnaire questions should return false ---
    @Test
    void hasSymptoms_emptyQuestionnaire_shouldReturnFalse() {
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(Collections.emptyList())
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of("any-key", "YES"))
                .build();

        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    // --- Unit Test 10: multiple questions, only one with symptom keyword and YES ---
    @Test
    void hasSymptoms_multipleQuestions_shouldDetectCoughAmongOthers() {
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        Question q1 = Question.builder()
                .id(q1Id)
                .text("Have you traveled recently?")
                .type(QuestionType.YES_NO)
                .build();

        Question q2 = Question.builder()
                .id(q2Id)
                .text("Do you have a cough?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q1, q2))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(
                        q1Id.toString(), "YES",
                        q2Id.toString(), "YES"
                ))
                .build();

        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }
}
