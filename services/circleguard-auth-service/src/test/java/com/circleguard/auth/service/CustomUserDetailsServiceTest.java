package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private LocalUserRepository repo;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        repo = mock(LocalUserRepository.class);
        service = new CustomUserDetailsService(repo);
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));
        assertTrue(ex.getMessage().contains("ghost"));
    }

    @Test
    void loadUserByUsername_inactiveUser_throwsDisabled() {
        LocalUser inactive = LocalUser.builder()
                .id(UUID.randomUUID()).username("alice").password("hash")
                .isActive(false).roles(Set.of()).build();
        when(repo.findByUsername("alice")).thenReturn(Optional.of(inactive));

        assertThrows(DisabledException.class,
                () -> service.loadUserByUsername("alice"));
    }

    @Test
    void loadUserByUsername_activeUser_returnsDetailsWithRolesAndPermissionsFlattened() {
        Permission readId = Permission.builder().id(UUID.randomUUID()).name("identity:lookup").build();
        Permission validate = Permission.builder().id(UUID.randomUUID()).name("survey:validate").build();

        Role staff = Role.builder()
                .id(UUID.randomUUID()).name("HEALTH_STAFF")
                .permissions(Set.of(readId, validate)).build();

        LocalUser active = LocalUser.builder()
                .id(UUID.randomUUID()).username("alice").password("hash")
                .isActive(true).roles(Set.of(staff)).build();

        when(repo.findByUsername("alice")).thenReturn(Optional.of(active));

        UserDetails details = service.loadUserByUsername("alice");

        assertEquals("alice", details.getUsername());
        assertEquals("hash", details.getPassword());
        // Authorities must include ROLE_<name> + every permission name
        var authorityNames = details.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();
        assertTrue(authorityNames.contains("ROLE_HEALTH_STAFF"),
                "role names must be prefixed with ROLE_ to align with Spring Security conventions");
        assertTrue(authorityNames.contains("identity:lookup"));
        assertTrue(authorityNames.contains("survey:validate"));
    }
}
