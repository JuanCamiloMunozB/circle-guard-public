package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocationResolutionServiceTest {

    private AccessPointRepository apRepo;
    private MacSessionRegistry sessionRegistry;
    private GraphService graphService;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOps = mock(SetOperations.class);
    private LocationResolutionService service;

    @BeforeEach
    void setUp() {
        apRepo = mock(AccessPointRepository.class);
        sessionRegistry = mock(MacSessionRegistry.class);
        graphService = mock(GraphService.class);
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForSet()).thenReturn(setOps);
        service = new LocationResolutionService(apRepo, sessionRegistry, graphService, kafkaTemplate, redis);
    }

    @Test
    void processSignal_unknownAp_logsAndReturns() {
        when(apRepo.findByMacAddress("UNKNOWN")).thenReturn(Optional.empty());

        service.processSignal("UNKNOWN", "DEV", -50.0);

        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(graphService);
    }

    @Test
    void processSignal_unmappedDevice_logsAndReturnsWithoutEmittingEvent() {
        AccessPoint ap = buildAp();
        when(apRepo.findByMacAddress("AP-MAC")).thenReturn(Optional.of(ap));
        when(sessionRegistry.getAnonymousId("DEV-MAC")).thenReturn(null);

        service.processSignal("AP-MAC", "DEV-MAC", -50.0);

        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(graphService);
    }

    @Test
    void processSignal_validResolution_emitsKafkaAndUpdatesGraph() {
        AccessPoint ap = buildAp();
        when(apRepo.findByMacAddress("AP-MAC")).thenReturn(Optional.of(ap));
        when(sessionRegistry.getAnonymousId("DEV-MAC")).thenReturn("anon-1");
        // No other users at this location yet
        when(setOps.members(anyString())).thenReturn(Set.of());

        service.processSignal("AP-MAC", "DEV-MAC", -45.0);

        verify(kafkaTemplate).send(eq("proximity.detected"), eq("anon-1"), any());
        verify(setOps).add(anyString(), eq("anon-1"));
        verify(graphService).detectAndFormCircles(ap.getId().toString());
    }

    @Test
    void processSignal_otherUsersPresent_recordsEncounterForEach() {
        AccessPoint ap = buildAp();
        when(apRepo.findByMacAddress("AP-MAC")).thenReturn(Optional.of(ap));
        when(sessionRegistry.getAnonymousId("DEV-MAC")).thenReturn("anon-self");
        // Three users already at this location, one is self -> should be skipped
        when(setOps.members(anyString())).thenReturn(Set.of("anon-other1", "anon-other2", "anon-self"));

        service.processSignal("AP-MAC", "DEV-MAC", -45.0);

        verify(graphService).recordEncounter(eq("anon-self"), eq("anon-other1"), eq(ap.getId().toString()));
        verify(graphService).recordEncounter(eq("anon-self"), eq("anon-other2"), eq(ap.getId().toString()));
        // Self must NOT be paired with self
        verify(graphService, never()).recordEncounter(eq("anon-self"), eq("anon-self"), anyString());
    }

    private AccessPoint buildAp() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.builder().id(buildingId).name("B").build();
        Floor floor = Floor.builder().id(UUID.randomUUID()).floorNumber(2).name("F2").building(building).build();
        return AccessPoint.builder().id(UUID.randomUUID())
                .macAddress("AP-MAC").coordinateX(1.0).coordinateY(2.0).name("AP-1")
                .floor(floor).build();
    }
}
