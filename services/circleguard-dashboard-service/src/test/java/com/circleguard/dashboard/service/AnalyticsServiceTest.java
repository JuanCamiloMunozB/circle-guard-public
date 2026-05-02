package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PromotionClient promotionClient;

    private KAnonymityFilter kAnonymityFilter;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        kAnonymityFilter = new KAnonymityFilter();
        analyticsService = new AnalyticsService(jdbcTemplate, promotionClient, kAnonymityFilter);
    }

    // --- Unit Test: getCampusSummary should delegate to PromotionClient ---
    @Test
    void getCampusSummary_shouldDelegateToPromotionClient() {
        Map<String, Object> expected = Map.of("totalUsers", 500, "confirmedCount", 3);
        when(promotionClient.getHealthStats()).thenReturn(expected);

        Map<String, Object> result = analyticsService.getCampusSummary();

        assertEquals(expected, result);
        verify(promotionClient).getHealthStats();
    }

    // --- Unit Test: getDepartmentStats with small population should apply k-anonymity masking ---
    @Test
    void getDepartmentStats_smallPopulation_shouldMaskResult() {
        Map<String, Object> rawStats = Map.of("totalUsers", 2, "department", "Mathematics");
        when(promotionClient.getHealthStatsByDepartment("Mathematics")).thenReturn(rawStats);

        Map<String, Object> result = analyticsService.getDepartmentStats("Mathematics");

        assertEquals("<5", result.get("totalUsers"));
        assertTrue(result.containsKey("note"));
    }

    // --- Unit Test: getDepartmentStats with sufficient population should NOT mask total ---
    @Test
    void getDepartmentStats_sufficientPopulation_shouldNotMaskTotal() {
        Map<String, Object> rawStats = Map.of("totalUsers", 100, "confirmedCount", 2);
        when(promotionClient.getHealthStatsByDepartment("Engineering")).thenReturn(rawStats);

        Map<String, Object> result = analyticsService.getDepartmentStats("Engineering");

        assertEquals(100, result.get("totalUsers"));
        assertFalse(result.containsKey("note"));
    }

    // --- Unit Test: getTimeSeries falls back to mock data when table does not exist ---
    @Test
    void getTimeSeries_tableNotFound_shouldReturnMockData() {
        when(jdbcTemplate.queryForList(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Table not found"));

        List<Map<String, Object>> result = analyticsService.getTimeSeries("hourly", 5);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(m -> m.containsKey("status") && m.containsKey("total")));
    }

    // --- Unit Test: getTimeSeries for daily period queries with day truncation ---
    @Test
    void getTimeSeries_dailyPeriod_shouldReturnRows() {
        List<Map<String, Object>> rows = List.of(
                Map.of("bucket", "2026-05-01", "status", "ACTIVE", "total", 180)
        );
        when(jdbcTemplate.queryForList(contains("day"), eq(10))).thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.getTimeSeries("daily", 10);

        assertFalse(result.isEmpty());
        assertEquals("ACTIVE", result.get(0).get("status"));
    }
}
