package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.MacSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionHandshakeController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionHandshakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MacSessionRegistry sessionRegistry;

    @Test
    void handshake_validBody_registersSession() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"AA:BB\",\"anonymousId\":\"anon-1\"}"))
                .andExpect(status().isOk());

        verify(sessionRegistry).registerSession("AA:BB", "anon-1");
    }

    @Test
    void handshake_missingMac_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"anon-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeSession_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/{mac}", "AA:BB"))
                .andExpect(status().isNoContent());

        verify(sessionRegistry).closeSession("AA:BB");
    }
}
