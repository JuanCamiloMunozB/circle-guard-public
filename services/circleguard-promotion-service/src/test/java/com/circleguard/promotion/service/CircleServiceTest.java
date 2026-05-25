package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CircleServiceTest {

    private CircleNodeRepository circleRepo;
    private HealthStatusService healthStatusService;
    private CircleService service;

    @BeforeEach
    void setUp() {
        circleRepo = mock(CircleNodeRepository.class);
        healthStatusService = mock(HealthStatusService.class);
        service = new CircleService(circleRepo, healthStatusService);
    }

    @Test
    void createCircle_generatesInviteCodeAndPersists() {
        when(circleRepo.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CircleNode created = service.createCircle("Lab partners", "loc-1");

        assertNotNull(created.getInviteCode());
        assertTrue(created.getInviteCode().startsWith("MESH-"),
                "invite codes must follow the MESH-XXXX format");
        assertEquals("Lab partners", created.getName());
        assertEquals("loc-1", created.getLocationId());
        assertTrue(created.getIsActive());
    }

    @Test
    void joinCircle_validCode_returnsCircleNode() {
        CircleNode result = new CircleNode();
        when(circleRepo.joinCircle("anon-1", "MESH-XXXX")).thenReturn(Optional.of(result));

        assertSame(result, service.joinCircle("anon-1", "MESH-XXXX"));
    }

    @Test
    void joinCircle_invalidCode_throws() {
        when(circleRepo.joinCircle(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.joinCircle("anon-1", "BAD"));
    }

    @Test
    void addMember_success_returnsCircleNode() {
        CircleNode result = new CircleNode();
        when(circleRepo.addUserToCircle("anon-2", 5L)).thenReturn(Optional.of(result));

        assertSame(result, service.addMember(5L, "anon-2"));
    }

    @Test
    void addMember_failure_throws() {
        when(circleRepo.addUserToCircle(anyString(), any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.addMember(5L, "anon-2"));
    }

    @Test
    void getUserCircles_delegatesToRepository() {
        List<CircleNode> circles = List.of(new CircleNode());
        when(circleRepo.findCirclesByUser("anon-1")).thenReturn(circles);

        assertEquals(circles, service.getUserCircles("anon-1"));
    }

    @Test
    void toggleCircleValidity_fromValidToInvalid_triggersPulseRecoveryForMembers() {
        UserNode member1 = new UserNode();
        member1.setAnonymousId("anon-1");
        UserNode member2 = new UserNode();
        member2.setAnonymousId("anon-2");
        Set<UserNode> members = new HashSet<>(Set.of(member1, member2));

        CircleNode circle = CircleNode.builder().id(1L).isValid(true).members(members).build();
        when(circleRepo.findById(1L)).thenReturn(Optional.of(circle));
        when(circleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.toggleCircleValidity(1L);

        assertFalse(circle.getIsValid(), "validity must be toggled to false");
        // Each member's status must be resolved (pulse recovery)
        verify(healthStatusService).resolveStatus("anon-1");
        verify(healthStatusService).resolveStatus("anon-2");
    }

    @Test
    void toggleCircleValidity_fromInvalidToValid_doesNotTriggerPulseRecovery() {
        CircleNode circle = CircleNode.builder().id(1L).isValid(false)
                .members(new HashSet<>()).build();
        when(circleRepo.findById(1L)).thenReturn(Optional.of(circle));
        when(circleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.toggleCircleValidity(1L);

        assertTrue(circle.getIsValid());
        // Pulse recovery only runs when toggling TO invalid
        verifyNoInteractions(healthStatusService);
    }

    @Test
    void toggleCircleValidity_missingCircle_throws() {
        when(circleRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.toggleCircleValidity(99L));
    }

    @Test
    void forceFenceCircle_promotesOnlyActiveMembersToProbable() {
        UserNode active = new UserNode();
        active.setAnonymousId("anon-active");
        active.setStatus("ACTIVE");
        UserNode probable = new UserNode();
        probable.setAnonymousId("anon-probable");
        probable.setStatus("PROBABLE");

        CircleNode circle = CircleNode.builder().id(1L)
                .members(new HashSet<>(Set.of(active, probable))).build();
        when(circleRepo.findById(1L)).thenReturn(Optional.of(circle));
        when(circleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.forceFenceCircle(1L);

        assertTrue(circle.getForceFence());
        // Only the ACTIVE member is promoted; the already-PROBABLE one is left alone
        verify(healthStatusService).updateStatus("anon-active", "PROBABLE");
        verify(healthStatusService, never()).updateStatus(eq("anon-probable"), anyString());
    }
}
