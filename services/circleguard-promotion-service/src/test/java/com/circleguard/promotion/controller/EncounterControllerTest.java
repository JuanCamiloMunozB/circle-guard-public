package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.service.AutoCircleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EncounterController.class)
@AutoConfigureMockMvc(addFilters = false)
class EncounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNodeRepository userRepository;
    @MockBean
    private AutoCircleService autoCircleService;

    @Test
    void reportEncounter_withLocation_persistsAndEvaluates() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"anon-A\",\"targetId\":\"anon-B\",\"locationId\":\"loc-7\"}"))
                .andExpect(status().isOk());

        verify(userRepository).recordEncounter(eq("anon-A"), eq("anon-B"), anyLong(), eq("loc-7"));
        verify(autoCircleService).evaluateEncounter("anon-A", "anon-B");
    }

    @Test
    void reportEncounter_noLocation_fallsBackToMobileBleSentinel() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"anon-A\",\"targetId\":\"anon-B\"}"))
                .andExpect(status().isOk());

        verify(userRepository).recordEncounter(eq("anon-A"), eq("anon-B"), anyLong(), eq("mobile_ble"));
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void toggleValidity_healthCenter_callsRepository() throws Exception {
        mockMvc.perform(patch("/api/v1/encounters/{id}/validity", 42L))
                .andExpect(status().isOk());
        verify(userRepository).toggleEncounterValidity(42L);
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void forceFence_healthCenter_callsRepository() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/{id}/force-fence", 99L))
                .andExpect(status().isOk());
        verify(userRepository).forceEncounterFence(99L);
    }
}
