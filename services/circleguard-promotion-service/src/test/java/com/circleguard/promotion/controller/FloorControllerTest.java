package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.service.AccessPointService;
import com.circleguard.promotion.service.FloorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FloorController.class)
@AutoConfigureMockMvc(addFilters = false)
class FloorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FloorService floorService;
    @MockBean
    private AccessPointService accessPointService;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void addAccessPoint_admin_returnsDto() throws Exception {
        UUID floorId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(UUID.randomUUID())
                .macAddress("MAC").coordinateX(1.0).coordinateY(2.0).name("AP").build();
        when(accessPointService.registerAccessPoint(any(), any(), any(), any(), any())).thenReturn(ap);

        mockMvc.perform(post("/api/v1/floors/{id}/access-points", floorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"MAC\",\"coordinateX\":1.0,\"coordinateY\":2.0,\"name\":\"AP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("MAC"));
    }

    @Test
    void getAccessPoints_returnsList() throws Exception {
        UUID floorId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(UUID.randomUUID())
                .macAddress("MAC").coordinateX(1.0).coordinateY(2.0).name("AP").build();
        when(accessPointService.getAccessPointsByFloor(floorId)).thenReturn(List.of(ap));

        mockMvc.perform(get("/api/v1/floors/{id}/access-points", floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateFloor_admin_returnsDto() throws Exception {
        UUID floorId = UUID.randomUUID();
        Floor updated = Floor.builder().id(floorId).floorNumber(3).name("F3").build();
        when(floorService.updateFloor(any(), any(), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/floors/{id}", floorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":3,\"name\":\"F3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("F3"));
    }

    @Test
    void deleteFloor_returnsOk() throws Exception {
        UUID floorId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/floors/{id}", floorId))
                .andExpect(status().isOk());

        verify(floorService).deleteFloor(floorId);
    }
}
