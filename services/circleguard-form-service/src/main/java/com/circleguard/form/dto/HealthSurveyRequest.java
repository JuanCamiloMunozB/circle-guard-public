package com.circleguard.form.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound payload for submitting a health survey. Deliberately a plain POJO
 * (not the {@code HealthSurvey} JPA entity) so a client cannot bind
 * server-controlled fields such as {@code id}, {@code validationStatus} or
 * {@code validatedBy} (mass-assignment / privilege escalation). The controller
 * maps it onto the entity, populating only client-owned fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthSurveyRequest {
    private UUID anonymousId;
    private Boolean hasFever;
    private Boolean hasCough;
    private String otherSymptoms;
    private LocalDate exposureDate;
    private Map<String, Object> responses;
    private String attachmentPath;
}
