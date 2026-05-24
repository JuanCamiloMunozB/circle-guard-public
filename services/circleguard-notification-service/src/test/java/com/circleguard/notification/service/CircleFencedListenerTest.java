package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CircleFencedListenerTest {

    private RoomReservationService roomService;
    private CircleFencedListener listener;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomReservationService.class);
        listener = new CircleFencedListener(new ObjectMapper(), roomService);
    }

    @Test
    void handleCircleFenced_validPayload_cancelsReservation() {
        String json = "{\"circleId\":\"c-1\",\"locationId\":\"loc-7\"}";

        listener.handleCircleFenced(json);

        verify(roomService).cancelReservation(eq("c-1"), eq("loc-7"));
    }

    @Test
    void handleCircleFenced_missingLocation_skipsCancellation() {
        String json = "{\"circleId\":\"c-2\",\"locationId\":\"\"}";

        listener.handleCircleFenced(json);

        verify(roomService, never()).cancelReservation(any(), any());
    }

    @Test
    void handleCircleFenced_nullLocation_skipsCancellation() {
        String json = "{\"circleId\":\"c-3\"}"; // no locationId field at all

        listener.handleCircleFenced(json);

        verify(roomService, never()).cancelReservation(any(), any());
    }

    @Test
    void handleCircleFenced_malformedJson_swallowedAndDoesNotCrash() {
        String broken = "{not-json";

        // The listener catches parse exceptions and logs; no rethrow.
        listener.handleCircleFenced(broken);

        verify(roomService, never()).cancelReservation(any(), any());
    }
}
