package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RoomReservationServiceImplTest {

    @Test
    void cancelReservation_returnsCompletedFuture() throws Exception {
        RoomReservationServiceImpl svc = new RoomReservationServiceImpl();
        ReflectionTestUtils.setField(svc, "roomBookingApiUrl", "http://facilities.local/api/v1/rooms");

        CompletableFuture<Void> future = svc.cancelReservation("circle-42", "loc-7");

        assertTrue(future.isDone());
        assertNull(future.get(), "stubbed cancellation completes with null payload");
    }
}
