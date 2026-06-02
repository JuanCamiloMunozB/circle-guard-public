package com.circleguard.form.controller;

import com.circleguard.form.dto.QuestionnaireRequest;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/questionnaires")
@RequiredArgsConstructor
public class QuestionnaireController {
    private final QuestionnaireService service;

    @GetMapping
    public ResponseEntity<List<Questionnaire>> getAll() {
        return ResponseEntity.ok(service.getAllQuestionnaires());
    }

    @GetMapping("/active")
    public ResponseEntity<Questionnaire> getActive() {
        return service.getActiveQuestionnaire()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Questionnaire> create(@RequestBody QuestionnaireRequest request) {
        // Build fresh entities from the request POJO so the client cannot bind
        // server-managed fields (id, timestamps) or the entity back-reference.
        Questionnaire questionnaire = Questionnaire.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .version(request.getVersion())
                .isActive(request.getIsActive())
                .build();
        if (request.getQuestions() != null) {
            List<Question> questions = request.getQuestions().stream()
                    .map(q -> Question.builder()
                            .text(q.getText())
                            .type(q.getType())
                            .options(q.getOptions())
                            .orderIndex(q.getOrderIndex())
                            .questionnaire(questionnaire)
                            .build())
                    .collect(Collectors.toList());
            questionnaire.setQuestions(questions);
        }
        return ResponseEntity.ok(service.saveQuestionnaire(questionnaire));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        service.activateQuestionnaire(id);
        return ResponseEntity.ok().build();
    }
}
