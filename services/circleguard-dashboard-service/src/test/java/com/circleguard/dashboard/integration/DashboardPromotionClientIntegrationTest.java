package com.circleguard.dashboard.integration;

import com.circleguard.dashboard.client.PromotionClient;
import com.circleguard.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: verifies AnalyticsService correctly wires PromotionClient
 * and applies K-Anonymity on top of responses from promotion-service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class DashboardPromotionClientIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @MockitoBean
    private PromotionClient promotionClient;

    @MockitoBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Integration Test 5: getCampusSummary wires through to PromotionClient within Spring context
    @Test
    void getCampusSummary_shouldReturnDataFromPromotionClient() {
        Map<String, Object> upstream = Map.of(
                "totalUsers", 350,
                "activeCount", 330,
                "suspectCount", 12,
                "confirmedCount", 8
        );
        when(promotionClient.getHealthStats()).thenReturn(upstream);

        Map<String, Object> result = analyticsService.getCampusSummary();

        assertNotNull(result);
        assertEquals(350, result.get("totalUsers"));
        assertEquals(330, result.get("activeCount"));
        verify(promotionClient, times(1)).getHealthStats();
    }

    // Integration Test 6: getDepartmentStats applies k-anonymity when population is small
    @Test
    void getDepartmentStats_smallDept_kAnonymityApplied() {
        Map<String, Object> rawStats = Map.of(
                "department", "Philosophy",
                "totalUsers", 3,
                "suspectCount", 1
        );
        when(promotionClient.getHealthStatsByDepartment("Philosophy")).thenReturn(rawStats);

        Map<String, Object> result = analyticsService.getDepartmentStats("Philosophy");

        assertEquals("<5", result.get("totalUsers"),
                "totalUsers below k=5 threshold must be masked");
        assertTrue(result.containsKey("note"),
                "Privacy note must be present when data is masked");
        verify(promotionClient).getHealthStatsByDepartment("Philosophy");
    }

    // Integration Test 7: PromotionClient unavailability returns error map without throwing
    @Test
    void getCampusSummary_promotionClientFails_shouldReturnErrorMap() {
        when(promotionClient.getHealthStats()).thenReturn(
                Map.of("error", "Service unavailable")
        );

        Map<String, Object> result = analyticsService.getCampusSummary();

        assertNotNull(result);
        assertTrue(result.containsKey("error"));
    }
}
