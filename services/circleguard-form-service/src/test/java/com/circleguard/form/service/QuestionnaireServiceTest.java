package com.circleguard.form.service;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionnaireServiceTest {

    private QuestionnaireRepository repository;
    private QuestionnaireService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuestionnaireRepository.class);
        service = new QuestionnaireService(repository);
    }

    @Test
    void getAllQuestionnaires_delegatesToRepository() {
        Questionnaire q1 = Questionnaire.builder().id(UUID.randomUUID()).build();
        Questionnaire q2 = Questionnaire.builder().id(UUID.randomUUID()).build();
        when(repository.findAll()).thenReturn(List.of(q1, q2));

        List<Questionnaire> out = service.getAllQuestionnaires();

        assertEquals(2, out.size());
        verify(repository).findAll();
    }

    @Test
    void getActiveQuestionnaire_returnsLatestActive() {
        Questionnaire active = Questionnaire.builder().version(5).isActive(true).build();
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.of(active));

        Optional<Questionnaire> out = service.getActiveQuestionnaire();

        assertTrue(out.isPresent());
        assertEquals(5, out.get().getVersion());
    }

    @Test
    void getActiveQuestionnaire_returnsEmptyWhenNoneActive() {
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.empty());

        assertTrue(service.getActiveQuestionnaire().isEmpty());
    }

    @Test
    void saveQuestionnaire_linksQuestionsBackToParent() {
        com.circleguard.form.model.Question q = com.circleguard.form.model.Question.builder()
                .id(UUID.randomUUID()).text("Fever?")
                .type(com.circleguard.form.model.QuestionType.YES_NO).build();
        Questionnaire toSave = Questionnaire.builder().questions(List.of(q)).build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Questionnaire saved = service.saveQuestionnaire(toSave);

        // each child question must now point at its parent — backref required by JPA cascade
        assertSame(saved, q.getQuestionnaire());
        verify(repository).save(toSave);
    }

    @Test
    void saveQuestionnaire_handlesNullQuestionsList() {
        Questionnaire toSave = Questionnaire.builder().questions(null).build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Questionnaire saved = service.saveQuestionnaire(toSave);

        assertNotNull(saved);
        verify(repository).save(toSave);
    }

    @Test
    void activateQuestionnaire_deactivatesPriorActivesAndActivatesTarget() {
        UUID targetId = UUID.randomUUID();
        Questionnaire previouslyActive = Questionnaire.builder()
                .id(UUID.randomUUID()).isActive(true).build();
        Questionnaire alreadyInactive = Questionnaire.builder()
                .id(UUID.randomUUID()).isActive(false).build();
        Questionnaire target = Questionnaire.builder()
                .id(targetId).isActive(false).build();

        when(repository.findAll()).thenReturn(List.of(previouslyActive, alreadyInactive, target));
        when(repository.findById(targetId)).thenReturn(Optional.of(target));

        service.activateQuestionnaire(targetId);

        assertFalse(previouslyActive.getIsActive(),
                "previously-active questionnaire must be deactivated to keep the invariant of one active");
        assertFalse(alreadyInactive.getIsActive(),
                "inactive ones stay inactive (and are not re-saved)");
        assertTrue(target.getIsActive(),
                "the target must end up active");
        // previouslyActive saved (deactivate) + target saved (activate) = 2 saves at minimum
        verify(repository, atLeast(2)).save(any());
    }

    @Test
    void activateQuestionnaire_targetMissing_isNoop() {
        UUID missing = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.activateQuestionnaire(missing));
        verify(repository, never()).save(any());
    }
}
