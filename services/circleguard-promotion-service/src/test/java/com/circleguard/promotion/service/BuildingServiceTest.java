package com.circleguard.promotion.service;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BuildingServiceTest {

    private BuildingRepository buildingRepo;
    private FloorRepository floorRepo;
    private BuildingService service;

    @BeforeEach
    void setUp() {
        buildingRepo = mock(BuildingRepository.class);
        floorRepo = mock(FloorRepository.class);
        service = new BuildingService(buildingRepo, floorRepo);
    }

    @Test
    void createBuilding_persistsAllFields() {
        when(buildingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Building created = service.createBuilding("Engineering", "ENG", "Main building",
                4.6, -74.1, "Cra 1 #19-27");

        ArgumentCaptor<Building> captor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepo).save(captor.capture());
        assertEquals("Engineering", captor.getValue().getName());
        assertEquals("ENG", captor.getValue().getCode());
        assertEquals(4.6, captor.getValue().getLatitude());
        assertSame(created, captor.getValue());
    }

    @Test
    void getAllBuildings_delegatesToRepository() {
        List<Building> all = List.of(Building.builder().id(UUID.randomUUID()).build());
        when(buildingRepo.findAll()).thenReturn(all);

        assertEquals(all, service.getAllBuildings());
    }

    @Test
    void updateBuilding_existing_appliesAllFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        Building existing = Building.builder().id(id).name("OLD").build();
        when(buildingRepo.findById(id)).thenReturn(Optional.of(existing));
        when(buildingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Building updated = service.updateBuilding(id, "NEW", "N", "desc",
                1.0, 2.0, "addr");

        assertEquals("NEW", updated.getName());
        assertEquals("N", updated.getCode());
        assertEquals(2.0, updated.getLongitude());
    }

    @Test
    void updateBuilding_missing_throws() {
        UUID id = UUID.randomUUID();
        when(buildingRepo.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateBuilding(id, "x", "x", "x", 0.0, 0.0, "x"));
    }

    @Test
    void deleteBuilding_noFloors_deletes() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findByBuildingId(id)).thenReturn(List.of());

        service.deleteBuilding(id);

        verify(buildingRepo).deleteById(id);
    }

    @Test
    void deleteBuilding_withFloors_refuses() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findByBuildingId(id)).thenReturn(List.of(
                Floor.builder().id(UUID.randomUUID()).build()
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));
        assertTrue(ex.getMessage().toLowerCase().contains("floors"));
        verify(buildingRepo, never()).deleteById(any());
    }
}
