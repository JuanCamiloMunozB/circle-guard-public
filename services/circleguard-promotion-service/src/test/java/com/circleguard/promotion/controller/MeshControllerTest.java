package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MeshController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserNodeRepository userRepository;

    @Test
    void getMeshStats_returnsBothCounts() throws Exception {
        when(userRepository.getConfirmedConnectionCount("anon-1")).thenReturn(8L);
        when(userRepository.getUnconfirmedConnectionCount("anon-1")).thenReturn(15L);

        mockMvc.perform(get("/api/v1/mesh/stats/{anonymousId}", "anon-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(8))
                .andExpect(jsonPath("$.unconfirmedCount").value(15));
    }
}
