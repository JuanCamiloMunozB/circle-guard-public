package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityVaultServiceTest {

    @Mock
    private IdentityMappingRepository repository;

    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "test-salt-for-unit-tests");
    }

    // --- Unit Test: same identity always returns the same anonymous UUID ---
    @Test
    void getOrCreateAnonymousId_sameIdentity_returnsSameUuid() {
        String identity = "student@university.edu";
        UUID existingId = UUID.randomUUID();

        IdentityMapping existing = IdentityMapping.builder()
                .anonymousId(existingId)
                .realIdentity(identity)
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(existing));

        UUID result1 = service.getOrCreateAnonymousId(identity);
        UUID result2 = service.getOrCreateAnonymousId(identity);

        assertEquals(result1, result2);
        assertEquals(existingId, result1);
        verify(repository, never()).save(any());
    }

    // --- Unit Test: new identity should be persisted with a generated UUID ---
    @Test
    void getOrCreateAnonymousId_newIdentity_shouldPersistMapping() {
        String identity = "new.student@university.edu";
        UUID generatedId = UUID.randomUUID();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(generatedId);
            return m;
        });

        UUID result = service.getOrCreateAnonymousId(identity);

        assertEquals(generatedId, result);
        ArgumentCaptor<IdentityMapping> captor = ArgumentCaptor.forClass(IdentityMapping.class);
        verify(repository).save(captor.capture());
        assertEquals(identity, captor.getValue().getRealIdentity());
        assertNotNull(captor.getValue().getSalt());
    }

    // --- Unit Test: different identities produce different hashes ---
    @Test
    void getOrCreateAnonymousId_differentIdentities_createDifferentMappings() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(UUID.randomUUID());
            return m;
        });

        UUID id1 = service.getOrCreateAnonymousId("alice@uni.edu");
        UUID id2 = service.getOrCreateAnonymousId("bob@uni.edu");

        assertNotEquals(id1, id2);
        verify(repository, times(2)).save(any());
    }

    // --- Unit Test: resolveRealIdentity for unknown UUID should throw NOT_FOUND ---
    @Test
    void resolveRealIdentity_unknownId_shouldThrowNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.resolveRealIdentity(unknownId));
    }

    // --- Unit Test: resolveRealIdentity for known UUID should return real identity ---
    @Test
    void resolveRealIdentity_knownId_shouldReturnRealIdentity() {
        UUID anonymousId = UUID.randomUUID();
        String realIdentity = "known.student@university.edu";

        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonymousId)
                .realIdentity(realIdentity)
                .build();

        when(repository.findById(anonymousId)).thenReturn(Optional.of(mapping));

        String result = service.resolveRealIdentity(anonymousId);

        assertEquals(realIdentity, result);
    }
}
