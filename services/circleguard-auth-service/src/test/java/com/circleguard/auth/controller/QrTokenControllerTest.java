package com.circleguard.auth.controller;

import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QrTokenController.class)
@Import(SecurityConfig.class)
class QrTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrTokenService qrService;

    // SecurityConfig dependencies must be present in the test context
    @MockitoBean
    private AuthenticationManager authManager;
    @MockitoBean
    private JwtTokenService jwtService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void generateToken_authenticated_returnsToken() throws Exception {
        when(qrService.generateQrToken(any(UUID.class))).thenReturn("qr.jwt.token");

        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value("qr.jwt.token"))
                .andExpect(jsonPath("$.expiresIn").value("60"));
    }

    @Test
    void generateToken_unauthenticated_isForbidden() throws Exception {
        // SecurityConfig declares this endpoint as .authenticated() without a custom
        // AuthenticationEntryPoint, so Spring Security's default behaviour for an
        // anonymous request is HTTP 403 (Forbidden) rather than 401 (Unauthorized).
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isForbidden());
    }
}
