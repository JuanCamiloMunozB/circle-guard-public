package com.circleguard.form.controller;

import com.circleguard.form.dto.HealthSurveyRequest;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.service.HealthSurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class HealthSurveyController {
    private final HealthSurveyService surveyService;

    @PostMapping
    public ResponseEntity<HealthSurvey> submit(@RequestBody HealthSurveyRequest request) {
        // Map only client-owned fields onto the entity; server-managed fields
        // (id, validationStatus, validatedBy) are never bound from the request.
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(request.getAnonymousId())
                .hasFever(request.getHasFever())
                .hasCough(request.getHasCough())
                .otherSymptoms(request.getOtherSymptoms())
                .exposureDate(request.getExposureDate())
                .responses(request.getResponses())
                .attachmentPath(request.getAttachmentPath())
                .build();
        return ResponseEntity.ok(surveyService.submitSurvey(survey));
    }
}
