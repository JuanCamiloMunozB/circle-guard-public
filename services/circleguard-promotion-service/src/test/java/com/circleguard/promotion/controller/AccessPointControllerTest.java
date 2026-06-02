package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.service.AccessPointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccessPointController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccessPointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessPointService accessPointService;

    @Test
    void getAccessPoint_existing_returnsDtoWithFloorId() throws Exception {
        UUID apId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).build();
        AccessPoint ap = AccessPoint.builder()
                .id(apId).macAddress("AA:BB:CC:DD:EE:FF")
                .floor(floor).coordinateX(1.0).coordinateY(2.0).name("AP-1").build();
        when(accessPointService.getAccessPoint(apId)).thenReturn(Optional.of(ap));

        mockMvc.perform(get("/api/v1/access-points/{id}", apId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.floorId").value(floorId.toString()))
                .andExpect(jsonPath("$.coordinateX").value(1.0));
    }

    @Test
    void getAccessPoint_missing_returns404() throws Exception {
        UUID apId = UUID.randomUUID();
        when(accessPointService.getAccessPoint(apId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/access-points/{id}", apId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateAccessPoint_admin_returnsUpdatedDto() throws Exception {
        UUID apId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(apId).macAddress("NEW:MAC")
                .coordinateX(5.0).coordinateY(6.0).name("AP-renamed").build();
        when(accessPointService.updateAccessPoint(any(), any(), any(), any(), any())).thenReturn(ap);

        mockMvc.perform(put("/api/v1/access-points/{id}", apId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"NEW:MAC\",\"coordinateX\":5.0,\"coordinateY\":6.0,\"name\":\"AP-renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP-renamed"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void deleteAccessPoint_admin_returnsOk() throws Exception {
        UUID apId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/access-points/{id}", apId))
                .andExpect(status().isOk());

        verify(accessPointService).deleteAccessPoint(apId);
    }
}
