package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.LocationResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocationSignalController.class)
@AutoConfigureMockMvc(addFilters = false)
class LocationSignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationResolutionService locationResolutionService;

    @Test
    void receiveSignal_validBody_callsService() throws Exception {
        mockMvc.perform(post("/api/v1/location/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apMac\":\"AP-MAC\",\"deviceMac\":\"DEV-MAC\",\"rssi\":-65}"))
                .andExpect(status().isOk());

        verify(locationResolutionService).processSignal("AP-MAC", "DEV-MAC", -65.0);
    }
}
