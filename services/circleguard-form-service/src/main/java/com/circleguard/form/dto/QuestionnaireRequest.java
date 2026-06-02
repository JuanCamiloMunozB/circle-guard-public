package com.circleguard.form.dto;

import com.circleguard.form.model.QuestionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Inbound payload for creating a questionnaire. A plain POJO instead of the
 * {@code Questionnaire} JPA entity so request binding cannot reach
 * server-managed fields ({@code id}, {@code createdAt}, {@code updatedAt}) or
 * the bidirectional entity graph. The controller maps it onto fresh entities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionnaireRequest {
    private String title;
    private String description;
    private Integer version;
    private Boolean isActive;
    private List<QuestionRequest> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionRequest {
        private String text;
        private QuestionType type;
        private String options;
        private Integer orderIndex;
    }
}
