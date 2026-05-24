package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemSettingsRepository settingsRepository;

    @Test
    void getSettings_existing_returnsCurrentSettings() throws Exception {
        SystemSettings s = SystemSettings.builder()
                .unconfirmedFencingEnabled(true).autoThresholdSeconds(7200L)
                .mandatoryFenceDays(14).encounterWindowDays(14).build();
        when(settingsRepository.getSettings()).thenReturn(Optional.of(s));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoThresholdSeconds").value(7200));
    }

    @Test
    void getSettings_missing_initializesDefaults() throws Exception {
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                // defaults: unconfirmedFencingEnabled=true, autoThreshold=3600, fenceDays=14, encWindow=14
                .andExpect(jsonPath("$.autoThresholdSeconds").value(3600))
                .andExpect(jsonPath("$.mandatoryFenceDays").value(14));

        verify(settingsRepository).save(any());
    }

    @Test
    void updateSettings_partialPayload_appliesOnlyNonNullFields() throws Exception {
        SystemSettings existing = SystemSettings.builder()
                .unconfirmedFencingEnabled(true).autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14).encounterWindowDays(14).build();
        when(settingsRepository.getSettings()).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unconfirmedFencingEnabled\":false,\"mandatoryFenceDays\":21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(false))
                .andExpect(jsonPath("$.mandatoryFenceDays").value(21))
                // unchanged fields preserved
                .andExpect(jsonPath("$.autoThresholdSeconds").value(3600));
    }

    @Test
    void toggleUnconfirmedFencing_setsFlagAndSaves() throws Exception {
        SystemSettings existing = SystemSettings.builder()
                .unconfirmedFencingEnabled(true).autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14).encounterWindowDays(14).build();
        when(settingsRepository.getSettings()).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/settings/toggle-unconfirmed-fencing")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(false));
    }
}
