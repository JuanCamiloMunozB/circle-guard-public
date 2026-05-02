package com.circleguard.promotion.service;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusLifecycleServiceTest {

    @Mock private Neo4jClient neo4jClient;
    @Mock private SystemSettingsRepository systemSettingsRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private StatusLifecycleService lifecycleService;

    private SystemSettings defaultSettings() {
        return SystemSettings.builder()
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .build();
    }

    // --- Unit Test: when no expired users found, Kafka should not be called ---
    @Test
    void processAutomaticTransitions_noExpiredUsers_shouldNotPublishEvents() {
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(defaultSettings()));

        Neo4jClient.UnboundRunnableSpec logSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        Neo4jClient.UnboundRunnableSpec updateSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(contains("RETURN u.anonymousId as id"))).thenReturn(logSpec);
        when(logSpec.bind(anyLong()).to(anyString()).fetch().all()).thenReturn(Collections.emptyList());

        when(neo4jClient.query(contains("SET u.status = 'ACTIVE'"))).thenReturn(updateSpec);

        Map<String, Object> emptyResult = Map.of("releasedIds", Collections.emptyList());
        when(updateSpec.bind(anyLong()).to(anyString()).fetch().one()).thenReturn(Optional.of(emptyResult));

        lifecycleService.processAutomaticTransitions();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // --- Unit Test: expired users should be released and Kafka events published ---
    @Test
    void processAutomaticTransitions_expiredUsers_shouldPublishStatusChangedEvents() {
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(defaultSettings()));

        Neo4jClient.UnboundRunnableSpec logSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        Neo4jClient.UnboundRunnableSpec updateSpec = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);

        when(neo4jClient.query(contains("RETURN u.anonymousId as id"))).thenReturn(logSpec);
        when(logSpec.bind(anyLong()).to(anyString()).fetch().all()).thenReturn(Collections.emptyList());

        when(neo4jClient.query(contains("SET u.status = 'ACTIVE'"))).thenReturn(updateSpec);

        List<String> releasedIds = List.of("user-001", "user-002");
        Map<String, Object> result = Map.of("releasedIds", releasedIds);
        when(updateSpec.bind(anyLong()).to(anyString()).fetch().one()).thenReturn(Optional.of(result));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        lifecycleService.processAutomaticTransitions();

        verify(kafkaTemplate, times(2)).send(eq("promotion.status.changed"), anyString(), any(Map.class));
    }

    // --- Unit Test: missing SystemSettings should throw IllegalStateException ---
    @Test
    void processAutomaticTransitions_missingSettings_shouldThrowException() {
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> lifecycleService.processAutomaticTransitions()
        );
    }
}
