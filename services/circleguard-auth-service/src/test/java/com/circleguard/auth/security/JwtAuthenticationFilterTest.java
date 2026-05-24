package com.circleguard.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "filter-jwt-secret-32-chars-kept-safe!";

    private JwtAuthenticationFilter filter;
    private Key key;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void noAuthorizationHeader_chainContinuesAndContextStaysEmpty() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void nonBearerHeader_isIgnored() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validBearerToken_setsAuthenticationWithPermissions() throws Exception {
        UUID subject = UUID.randomUUID();
        String token = Jwts.builder()
                .setSubject(subject.toString())
                .claim("permissions", List.of("ROLE_USER", "survey:read"))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "valid token must populate the security context");
        assertEquals(subject.toString(), auth.getName());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("survey:read")));
        verify(chain).doFilter(req, res);
    }

    @Test
    void validBearerToken_withoutPermissionsClaim_setsAuthWithEmptyAuthorities() throws Exception {
        String token = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(req, res, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
    }

    @Test
    void invalidSignature_clearsContextWithoutFailingTheChain() throws Exception {
        // Token signed with a DIFFERENT key -> signature mismatch -> caught -> context cleared
        Key otherKey = Keys.hmacShaKeyFor("other-secret-32-chars-also-kept-safe!".getBytes());
        String token = Jwts.builder()
                .setSubject("anything")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "invalid token must NOT populate context (security guarantee)");
        verify(chain).doFilter(req, res);
    }
}
