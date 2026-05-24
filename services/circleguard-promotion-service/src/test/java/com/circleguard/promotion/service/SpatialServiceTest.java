package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
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

/**
 * SpatialService is a higher-level facade over Building/Floor/AccessPoint
 * repositories. The dedicated services (BuildingService etc.) are tested in
 * their own files; here we cover SpatialService's specific orchestration:
 * cascading deletion guards, parent-lookup-or-throw, and field application.
 */
class SpatialServiceTest {

    private BuildingRepository buildingRepo;
    private FloorRepository floorRepo;
    private AccessPointRepository apRepo;
    private SpatialService service;

    @BeforeEach
    void setUp() {
        buildingRepo = mock(BuildingRepository.class);
        floorRepo = mock(FloorRepository.class);
        apRepo = mock(AccessPointRepository.class);
        service = new SpatialService(buildingRepo, floorRepo, apRepo);
    }

    // ----- createBuilding -----
    @Test
    void createBuilding_persistsBuilderFields() {
        when(buildingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Building b = service.createBuilding("Engineering", "ENG", "Main");

        assertEquals("Engineering", b.getName());
        assertEquals("ENG", b.getCode());
        verify(buildingRepo).save(any(Building.class));
    }

    // ----- addFloor -----
    @Test
    void addFloor_existingBuilding_linksAndSaves() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.builder().id(buildingId).build();
        when(buildingRepo.findById(buildingId)).thenReturn(Optional.of(building));
        when(floorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Floor floor = service.addFloor(buildingId, 1, "Lobby");

        assertSame(building, floor.getBuilding());
        assertEquals(1, floor.getFloorNumber());
        assertEquals("Lobby", floor.getName());
    }

    @Test
    void addFloor_missingBuilding_throws() {
        UUID buildingId = UUID.randomUUID();
        when(buildingRepo.findById(buildingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.addFloor(buildingId, 1, "X"));
        verify(floorRepo, never()).save(any());
    }

    @Test
    void getAllBuildings_delegates() {
        when(buildingRepo.findAll()).thenReturn(List.of(Building.builder().build()));
        assertEquals(1, service.getAllBuildings().size());
    }

    @Test
    void getFloorsByBuilding_delegates() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findByBuildingId(id)).thenReturn(List.of(Floor.builder().build()));
        assertEquals(1, service.getFloorsByBuilding(id).size());
    }

    // ----- updateBuilding -----
    @Test
    void updateBuilding_existing_appliesAllFields() {
        UUID id = UUID.randomUUID();
        Building existing = Building.builder().id(id).name("OLD").build();
        when(buildingRepo.findById(id)).thenReturn(Optional.of(existing));
        when(buildingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Building out = service.updateBuilding(id, "NEW", "N", "desc");

        assertEquals("NEW", out.getName());
        assertEquals("N", out.getCode());
        assertEquals("desc", out.getDescription());
    }

    @Test
    void updateBuilding_missing_throws() {
        UUID id = UUID.randomUUID();
        when(buildingRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.updateBuilding(id, "x", "x", "x"));
    }

    // ----- deleteBuilding -----
    @Test
    void deleteBuilding_noFloors_deletes() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findByBuildingId(id)).thenReturn(List.of());

        service.deleteBuilding(id);

        verify(buildingRepo).deleteById(id);
    }

    @Test
    void deleteBuilding_withFloors_refusesWithMessage() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findByBuildingId(id)).thenReturn(List.of(Floor.builder().build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));
        assertTrue(ex.getMessage().toLowerCase().contains("floor"));
        verify(buildingRepo, never()).deleteById(any());
    }

    // ----- updateFloor -----
    @Test
    void updateFloor_existing_appliesFields() {
        UUID id = UUID.randomUUID();
        Floor existing = Floor.builder().id(id).floorNumber(0).build();
        when(floorRepo.findById(id)).thenReturn(Optional.of(existing));
        when(floorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Floor out = service.updateFloor(id, 3, "F3");

        assertEquals(3, out.getFloorNumber());
        assertEquals("F3", out.getName());
    }

    @Test
    void updateFloor_missing_throws() {
        UUID id = UUID.randomUUID();
        when(floorRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.updateFloor(id, 1, "x"));
    }

    // ----- deleteFloor -----
    @Test
    void deleteFloor_noAccessPoints_deletes() {
        UUID id = UUID.randomUUID();
        when(apRepo.findByFloorId(id)).thenReturn(List.of());

        service.deleteFloor(id);

        verify(floorRepo).deleteById(id);
    }

    @Test
    void deleteFloor_withAccessPoints_refusesWithMessage() {
        UUID id = UUID.randomUUID();
        when(apRepo.findByFloorId(id)).thenReturn(List.of(AccessPoint.builder().build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteFloor(id));
        assertTrue(ex.getMessage().toLowerCase().contains("access point"));
        verify(floorRepo, never()).deleteById(any());
    }

    // ----- AccessPoint methods -----
    @Test
    void registerAccessPoint_existingFloor_linksAndSaves() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).build();
        when(floorRepo.findById(floorId)).thenReturn(Optional.of(floor));
        when(apRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPoint ap = service.registerAccessPoint(floorId, "MAC", 1.0, 2.0, "AP");

        assertSame(floor, ap.getFloor());
        assertEquals("MAC", ap.getMacAddress());
    }

    @Test
    void registerAccessPoint_missingFloor_throws() {
        UUID floorId = UUID.randomUUID();
        when(floorRepo.findById(floorId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "MAC", 1.0, 2.0, "AP"));
    }

    @Test
    void getAccessPoint_delegates() {
        UUID id = UUID.randomUUID();
        when(apRepo.findById(id)).thenReturn(Optional.of(AccessPoint.builder().build()));
        assertTrue(service.getAccessPoint(id).isPresent());
    }

    @Test
    void getAccessPointsByFloor_delegates() {
        UUID id = UUID.randomUUID();
        when(apRepo.findByFloorId(id)).thenReturn(List.of(AccessPoint.builder().build()));
        assertEquals(1, service.getAccessPointsByFloor(id).size());
    }

    @Test
    void updateAccessPoint_existing_appliesAllFields() {
        UUID id = UUID.randomUUID();
        AccessPoint existing = AccessPoint.builder().id(id).macAddress("OLD").build();
        when(apRepo.findById(id)).thenReturn(Optional.of(existing));
        when(apRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPoint out = service.updateAccessPoint(id, "NEW", 9.0, 9.0, "NEW-NAME");

        assertEquals("NEW", out.getMacAddress());
        assertEquals(9.0, out.getCoordinateX());
        assertEquals("NEW-NAME", out.getName());
    }

    @Test
    void updateAccessPoint_missing_throws() {
        UUID id = UUID.randomUUID();
        when(apRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> service.updateAccessPoint(id, "X", 0.0, 0.0, "X"));
    }

    @Test
    void deleteAccessPoint_delegates() {
        UUID id = UUID.randomUUID();

        service.deleteAccessPoint(id);

        verify(apRepo).deleteById(id);
    }
}
