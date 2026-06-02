package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalUserRepository repo;
    @MockitoBean
    private AuthenticationManager authManager;
    @MockitoBean
    private JwtTokenService jwtService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser
    void getUsersByPermission_returnsUsernameAndEmail() throws Exception {
        LocalUser a = LocalUser.builder()
                .id(UUID.randomUUID()).username("alice").email("alice@u.edu").build();
        LocalUser b = LocalUser.builder()
                .id(UUID.randomUUID()).username("bob").email(null).build();  // null email branch
        when(repo.findUsersByPermissionName("notify:emergency")).thenReturn(List.of(a, b));

        mockMvc.perform(get("/api/v1/users/permissions/{p}", "notify:emergency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].email").value("alice@u.edu"))
                .andExpect(jsonPath("$[1].username").value("bob"))
                // The controller maps null email to empty string to keep the JSON shape stable
                .andExpect(jsonPath("$[1].email").value(""));
    }

    @Test
    @WithMockUser
    void getUsersByPermission_emptyResult_returnsEmptyArray() throws Exception {
        when(repo.findUsersByPermissionName("nope")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/permissions/{p}", "nope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
