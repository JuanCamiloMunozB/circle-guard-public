package com.circleguard.dashboard.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Plain unit test for the happy path of PromotionClient. The Circuit Breaker
 * test covers the fallback path; here we drive the success path directly via
 * the visible-for-testing constructor so the URL building and the returned
 * body are asserted.
 */
class PromotionClientTest {

    private static final String BASE = "http://promotion-service:8088";

    @Test
    void getHealthStats_callsStatsEndpointAndReturnsBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, Object> body = Map.of("total", 42);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(body);

        PromotionClient client = new PromotionClient(restTemplate, BASE);

        Map<String, Object> result = client.getHealthStats();

        assertSame(body, result);
        verify(restTemplate).getForObject(
                eq(BASE + "/api/v1/health-status/stats"), eq(Map.class));
    }

    @Test
    void getHealthStatsByDepartment_buildsEncodedUrlAndReturnsBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, Object> body = Map.of("department", "Computer Science");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(body);

        PromotionClient client = new PromotionClient(restTemplate, BASE);

        Map<String, Object> result = client.getHealthStatsByDepartment("Computer Science");

        assertSame(body, result);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(url.capture(), eq(Map.class));
        assertEquals(
                BASE + "/api/v1/health-status/stats/department/Computer%20Science",
                url.getValue(),
                "the department must be path-encoded to avoid breaking the URL");
    }
}
