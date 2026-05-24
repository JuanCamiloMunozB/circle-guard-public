package com.circleguard.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DualChainAuthenticationProviderTest {

    private LdapAuthenticationProvider ldap;
    private DaoAuthenticationProvider local;
    private DualChainAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        ldap = mock(LdapAuthenticationProvider.class);
        local = mock(DaoAuthenticationProvider.class);
        provider = new DualChainAuthenticationProvider(ldap, local);
    }

    @Test
    void authenticate_ldapSucceeds_returnsLdapAuthAndLocalIsNeverCalled() {
        Authentication input = new UsernamePasswordAuthenticationToken("alice", "pw");
        Authentication ldapResult = new UsernamePasswordAuthenticationToken("alice", "pw");
        when(ldap.authenticate(input)).thenReturn(ldapResult);

        Authentication out = provider.authenticate(input);

        assertSame(ldapResult, out);
        verify(local, never()).authenticate(any());
    }

    @Test
    void authenticate_ldapFails_fallsBackToLocalProvider() {
        Authentication input = new UsernamePasswordAuthenticationToken("alice", "pw");
        Authentication localResult = new UsernamePasswordAuthenticationToken("alice", "pw");
        when(ldap.authenticate(input))
                .thenThrow(new BadCredentialsException("LDAP rejected"));
        when(local.authenticate(input)).thenReturn(localResult);

        Authentication out = provider.authenticate(input);

        assertSame(localResult, out);
    }

    @Test
    void authenticate_bothFail_propagatesLocalException() {
        Authentication input = new UsernamePasswordAuthenticationToken("alice", "pw");
        when(ldap.authenticate(input)).thenThrow(new BadCredentialsException("LDAP rejected"));
        BadCredentialsException localErr = new BadCredentialsException("Local rejected");
        when(local.authenticate(input)).thenThrow(localErr);

        AuthenticationException thrown = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(input));
        assertSame(localErr, thrown,
                "when both chains fail, the local (last-attempted) exception must surface");
    }

    @Test
    void supports_acceptsUsernamePasswordToken() {
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void supports_rejectsOtherAuthenticationTypes() {
        assertFalse(provider.supports(String.class));
    }
}
