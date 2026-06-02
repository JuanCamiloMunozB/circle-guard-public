package com.circleguard.identity.integration;

import com.circleguard.identity.service.IdentityVaultService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: verifies the full HTTP layer of IdentityVaultController —
 * JSON deserialization, security, service delegation, and response serialization.
 */
@WebMvcTest(com.circleguard.identity.controller.IdentityVaultController.class)
@Import(com.circleguard.identity.config.SecurityConfig.class)
@Tag("integration")
class IdentityMappingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityVaultService vaultService;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Integration Test 11: POST /api/v1/identities/map returns anonymousId for valid body
    @Test
    @WithMockUser
    void mapIdentity_validRequest_returnsAnonymousId() throws Exception {
        UUID expectedId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId(anyString())).thenReturn(expectedId);

        mockMvc.perform(post("/api/v1/identities/map")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realIdentity\":\"student@university.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(expectedId.toString()));

        verify(vaultService).getOrCreateAnonymousId("student@university.edu");
    }

    // Integration Test 12: POST /api/v1/identities/visitor creates visitor mapping with composite key
    @Test
    @WithMockUser
    void registerVisitor_validRequest_returnsAnonymousId() throws Exception {
        UUID visitorId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId(anyString())).thenReturn(visitorId);

        mockMvc.perform(post("/api/v1/identities/visitor")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Doe\",\"email\":\"visitor@external.com\",\"reason_for_visit\":\"Conference\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(visitorId.toString()));

        verify(vaultService).getOrCreateAnonymousId(
                argThat(id -> id.startsWith("VISITOR|") && id.contains("visitor@external.com"))
        );
    }
}
