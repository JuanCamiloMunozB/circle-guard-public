package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.service.BuildingService;
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

@WebMvcTest(controllers = BuildingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuildingService buildingService;
    @MockBean
    private FloorService floorService;

    @Test
    void listBuildings_returnsConvertedDtos() throws Exception {
        Building b = Building.builder().id(UUID.randomUUID()).name("Engineering Hall")
                .code("ENG").latitude(4.5).longitude(-74.1).address("Cra 1").build();
        when(buildingService.getAllBuildings()).thenReturn(List.of(b));

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Engineering Hall"))
                .andExpect(jsonPath("$[0].code").value("ENG"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createBuilding_admin_returnsDto() throws Exception {
        Building created = Building.builder().id(UUID.randomUUID())
                .name("New").code("NEW").latitude(0.0).longitude(0.0).build();
        when(buildingService.createBuilding(any(), any(), any(), any(), any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"code\":\"NEW\",\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void getFloors_returnsList() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Floor f1 = Floor.builder().id(UUID.randomUUID()).floorNumber(1).name("Lobby").build();
        when(floorService.getFloorsByBuilding(buildingId)).thenReturn(List.of(f1));

        mockMvc.perform(get("/api/v1/buildings/{id}/floors", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floorNumber").value(1));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void addFloor_admin_returnsDto() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Floor f = Floor.builder().id(UUID.randomUUID()).floorNumber(2).name("F2").build();
        when(floorService.addFloor(any(), any(), any())).thenReturn(f);

        mockMvc.perform(post("/api/v1/buildings/{id}/floors", buildingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorNumber\":2,\"name\":\"F2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("F2"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateBuilding_admin_returnsUpdatedDto() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Building updated = Building.builder().id(buildingId).name("Renamed").build();
        when(buildingService.updateBuilding(any(), any(), any(), any(), any(), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/buildings/{id}", buildingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deleteBuilding_returnsOk() throws Exception {
        UUID buildingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/buildings/{id}", buildingId))
                .andExpect(status().isOk());

        verify(buildingService).deleteBuilding(buildingId);
    }
}
