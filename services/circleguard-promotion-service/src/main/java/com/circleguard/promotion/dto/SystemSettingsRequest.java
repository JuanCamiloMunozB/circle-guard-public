package com.circleguard.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound payload for partial updates of the system settings. A plain POJO
 * instead of the {@code SystemSettings} JPA entity so a client cannot bind the
 * {@code id} (or any future server-managed column). All fields are nullable:
 * only non-null values are applied to the persisted settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemSettingsRequest {
    private Boolean unconfirmedFencingEnabled;
    private Long autoThresholdSeconds;
    private Integer mandatoryFenceDays;
    private Integer encounterWindowDays;
}
