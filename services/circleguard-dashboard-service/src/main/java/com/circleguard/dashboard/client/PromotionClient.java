package com.circleguard.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Date;
import java.util.Map;

/**
 * Client for promotion-service. Each remote call is wrapped in a Resilience4j
 * Circuit Breaker; when promotion-service is unhealthy (timeouts, 5xx, IO
 * failures) the breaker opens and the dedicated fallback method returns a
 * degraded payload instead of cascading the failure to the dashboard UI.
 *
 * Note: there is NO try/catch around the remote call on purpose. Swallowing
 * the exception would prevent Resilience4j from ever observing a failure,
 * which would in turn prevent the breaker from ever opening.
 *
 * Thresholds live in application.yml under
 * resilience4j.circuitbreaker.instances.promotionService.
 */
@Component
@Slf4j
public class PromotionClient {

    private static final String CB_NAME = "promotionService";

    private final RestTemplate restTemplate;
    private final String promotionServiceUrl;

    public PromotionClient(@Value("${circleguard.promotion-service.url:http://localhost:8088}") String promotionServiceUrl) {
        this(new RestTemplate(), promotionServiceUrl);
    }

    /**
     * Test-friendly constructor: lets unit tests inject a stub RestTemplate.
     */
    PromotionClient(RestTemplate restTemplate, String promotionServiceUrl) {
        this.restTemplate = restTemplate;
        this.promotionServiceUrl = promotionServiceUrl;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackGetHealthStats")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStats() {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats",
                Map.class
        );
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackGetHealthStatsByDepartment")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        String url = UriComponentsBuilder
                .fromHttpUrl(promotionServiceUrl)
                .path("/api/v1/health-status/stats/department/{department}")
                .build(department)
                .toString();
        return restTemplate.getForObject(url, Map.class);
    }

    /**
     * Fallback for getHealthStats. Returns a controlled "degraded" payload so
     * upstream callers can render an empty/error state without 5xx.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> fallbackGetHealthStats(Throwable cause) {
        log.warn("promotion-service unavailable on getHealthStats (cause: {})",
                cause.getClass().getSimpleName());
        return Map.of("error", "Service unavailable", "timestamp", new Date());
    }

    /**
     * Fallback for getHealthStatsByDepartment — preserves the queried department
     * in the payload so the UI can still render the correct label.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> fallbackGetHealthStatsByDepartment(String department, Throwable cause) {
        log.warn("promotion-service unavailable on getHealthStatsByDepartment={} (cause: {})",
                department, cause.getClass().getSimpleName());
        return Map.of("error", "Service unavailable", "department", department, "timestamp", new Date());
    }
}
