package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
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

class AccessPointServiceTest {

    private AccessPointRepository apRepo;
    private FloorRepository floorRepo;
    private AccessPointService service;

    @BeforeEach
    void setUp() {
        apRepo = mock(AccessPointRepository.class);
        floorRepo = mock(FloorRepository.class);
        service = new AccessPointService(apRepo, floorRepo);
    }

    @Test
    void registerAccessPoint_existingFloor_persistsWithFloorReference() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).build();
        when(floorRepo.findById(floorId)).thenReturn(Optional.of(floor));
        when(apRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPoint result = service.registerAccessPoint(floorId, "MAC-A", 1.0, 2.0, "AP-1");

        ArgumentCaptor<AccessPoint> captor = ArgumentCaptor.forClass(AccessPoint.class);
        verify(apRepo).save(captor.capture());
        assertSame(floor, captor.getValue().getFloor());
        assertEquals("MAC-A", captor.getValue().getMacAddress());
        assertEquals(1.0, captor.getValue().getCoordinateX());
        assertNotNull(result);
    }

    @Test
    void registerAccessPoint_unknownFloor_throws() {
        UUID floorId = UUID.randomUUID();
        when(floorRepo.findById(floorId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "MAC", 1.0, 2.0, "AP"));
        verify(apRepo, never()).save(any());
    }

    @Test
    void getAccessPoint_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).build();
        when(apRepo.findById(id)).thenReturn(Optional.of(ap));

        assertSame(ap, service.getAccessPoint(id).orElseThrow());
    }

    @Test
    void getAccessPointsByFloor_delegatesToRepository() {
        UUID floorId = UUID.randomUUID();
        List<AccessPoint> aps = List.of(AccessPoint.builder().id(UUID.randomUUID()).build());
        when(apRepo.findByFloorId(floorId)).thenReturn(aps);

        assertEquals(aps, service.getAccessPointsByFloor(floorId));
    }

    @Test
    void updateAccessPoint_existing_appliesAllFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        AccessPoint existing = AccessPoint.builder().id(id)
                .macAddress("OLD").coordinateX(0.0).coordinateY(0.0).name("OLD").build();
        when(apRepo.findById(id)).thenReturn(Optional.of(existing));
        when(apRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPoint updated = service.updateAccessPoint(id, "NEW", 5.0, 6.0, "NEW-NAME");

        assertEquals("NEW", updated.getMacAddress());
        assertEquals(5.0, updated.getCoordinateX());
        assertEquals(6.0, updated.getCoordinateY());
        assertEquals("NEW-NAME", updated.getName());
    }

    @Test
    void updateAccessPoint_missing_throws() {
        UUID id = UUID.randomUUID();
        when(apRepo.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateAccessPoint(id, "X", 0.0, 0.0, "X"));
    }

    @Test
    void deleteAccessPoint_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteAccessPoint(id);

        verify(apRepo).deleteById(id);
    }
}
