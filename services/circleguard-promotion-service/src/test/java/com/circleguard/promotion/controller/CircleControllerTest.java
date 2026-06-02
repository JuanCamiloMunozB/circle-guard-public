package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.service.CircleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CircleController.class)
@AutoConfigureMockMvc(addFilters = false)
class CircleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CircleService circleService;

    @Test
    void createCircle_returnsCircleNode() throws Exception {
        CircleNode node = new CircleNode();
        node.setId(1L);
        node.setName("Study Group");
        when(circleService.createCircle(any(), any())).thenReturn(node);

        mockMvc.perform(post("/api/v1/circles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Study Group\",\"locationId\":\"loc-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study Group"));
    }

    @Test
    void joinCircle_returnsCircleNode() throws Exception {
        CircleNode node = new CircleNode();
        node.setId(2L);
        when(circleService.joinCircle("anon-1", "ABC123")).thenReturn(node);

        mockMvc.perform(post("/api/v1/circles/join/{code}/user/{anonymousId}", "ABC123", "anon-1"))
                .andExpect(status().isOk());
    }

    @Test
    void addMember_returnsCircleNode() throws Exception {
        CircleNode node = new CircleNode();
        node.setId(3L);
        when(circleService.addMember(3L, "anon-2")).thenReturn(node);

        mockMvc.perform(post("/api/v1/circles/{id}/members/{anonymousId}", 3L, "anon-2"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserCircles_returnsList() throws Exception {
        when(circleService.getUserCircles(anyString())).thenReturn(List.of(new CircleNode()));

        mockMvc.perform(get("/api/v1/circles/user/{anonymousId}", "anon-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void toggleValidity_healthCenter_returnsOk() throws Exception {
        mockMvc.perform(patch("/api/v1/circles/{id}/validity", 5L))
                .andExpect(status().isOk());

        verify(circleService).toggleCircleValidity(5L);
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void forceFence_healthCenter_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/circles/{id}/force-fence", 7L))
                .andExpect(status().isOk());

        verify(circleService).forceFenceCircle(7L);
    }
}
