package com.circleguard.dashboard.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for the Circuit Breaker wrapping PromotionClient.
 *
 * Simulates promotion-service being unreachable: RestTemplate throws on every
 * call. Asserts:
 *   1. Fallback returns the degraded "Service unavailable" map (never throws).
 *   2. After saturating the sliding window with failures the breaker opens,
 *      protecting promotion-service from further requests.
 */
@SpringBootTest(
        classes = PromotionClientCircuitBreakerTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.promotionService.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.promotionService.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.instances.promotionService.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.promotionService.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.promotionService.permittedNumberOfCallsInHalfOpenState=1",
        "resilience4j.circuitbreaker.instances.promotionService.automaticTransitionFromOpenToHalfOpenEnabled=false",
        "resilience4j.circuitbreaker.instances.promotionService.recordExceptions=" +
                "org.springframework.web.client.RestClientException," +
                "java.io.IOException"
})
class PromotionClientCircuitBreakerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class TestApp {
        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        PromotionClient promotionClient(RestTemplate restTemplate) {
            return new PromotionClient(restTemplate, "http://promotion-service:8088");
        }
    }

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PromotionClient promotionClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        circuitBreakerRegistry.circuitBreaker("promotionService").reset();
        reset(restTemplate);
    }

    @Test
    void downstreamFailure_runsFallback_returnsDegradedMap() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result, "fallback must return a non-null map");
        assertEquals("Service unavailable", result.get("error"),
                "fallback must signal degraded state with the agreed key");
        assertTrue(result.containsKey("timestamp"),
                "fallback must include a timestamp so the UI can show staleness");
    }

    @Test
    void departmentVariant_preservesDepartmentInFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("Engineering");

        assertNotNull(result);
        assertEquals("Service unavailable", result.get("error"));
        assertEquals("Engineering", result.get("department"),
                "the department label must survive in the fallback for the UI");
    }

    @Test
    void repeatedFailures_openTheCircuit_andStopHittingTheDownstream() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("promotionService");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

        for (int i = 0; i < 4; i++) {
            assertNotNull(promotionClient.getHealthStats());
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                "after 4 failures (failureRate=100%) the breaker MUST be OPEN");

        for (int i = 0; i < 10; i++) {
            Map<String, Object> degraded = promotionClient.getHealthStats();
            assertEquals("Service unavailable", degraded.get("error"));
        }
        verify(restTemplate, times(4)).getForObject(anyString(), eq(Map.class));
    }
}
