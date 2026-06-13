package com.circleguard.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MacSessionRegistryTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private MacSessionRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        registry = new MacSessionRegistry(redis);
    }

    @Test
    void registerSession_normalizesMacToLowercaseAndSetsTtl() {
        registry.registerSession("AA:BB:CC:DD:EE:FF", "anon-1");

        verify(valueOps).set(eq("session:mac:aa:bb:cc:dd:ee:ff"),
                eq("anon-1"),
                eq(Duration.ofHours(8)));
    }

    @Test
    void getAnonymousId_normalizesAndReturnsValue() {
        when(valueOps.get("session:mac:aa:bb:cc:dd:ee:ff")).thenReturn("anon-99");

        assertEquals("anon-99", registry.getAnonymousId("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    void closeSession_deletesKeyByNormalizedMac() {
        registry.closeSession("AA:BB:CC:DD:EE:FF");

        verify(redis).delete("session:mac:aa:bb:cc:dd:ee:ff");
    }

    @Test
    void registerSession_rejectsMalformedMacAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerSession("not-a-mac", "anon-1"));
    }

    @Test
    void registerSession_rejectsInjectionInAnonymousId() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerSession("AA:BB:CC:DD:EE:FF", "anon 1; FLUSHALL"));
    }

    @Test
    void getAnonymousId_rejectsMalformedMacAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.getAnonymousId("XX:YY"));
    }
}
