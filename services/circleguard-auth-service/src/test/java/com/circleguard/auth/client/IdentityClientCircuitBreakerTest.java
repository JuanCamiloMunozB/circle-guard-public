package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for the Circuit Breaker wrapping IdentityClient.
 *
 * Simulates identity-service being unreachable: every RestTemplate call throws
 * RestClientException. The test asserts that:
 *   1. The fallback runs and returns Optional.empty() — the original exception
 *      is NOT propagated to the caller.
 *   2. After exhausting the sliding window with failures, the breaker
 *      transitions to OPEN, so the downstream service stops being hammered.
 *
 * A tiny @SpringBootConfiguration is used so we boot only the Resilience4j +
 * AOP autoconfig — not the full auth-service stack (no Postgres / LDAP / JPA).
 */
@SpringBootTest(
        classes = IdentityClientCircuitBreakerTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.identityService.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.identityService.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.instances.identityService.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.identityService.waitDurationInOpenState=60s",
        "resilience4j.circuitbreaker.instances.identityService.permittedNumberOfCallsInHalfOpenState=1",
        "resilience4j.circuitbreaker.instances.identityService.automaticTransitionFromOpenToHalfOpenEnabled=false",
        "resilience4j.circuitbreaker.instances.identityService.recordExceptions=" +
                "org.springframework.web.client.RestClientException," +
                "java.io.IOException," +
                "java.lang.IllegalStateException"
})
class IdentityClientCircuitBreakerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            LdapAutoConfiguration.class,
            EmbeddedLdapAutoConfiguration.class,
            SecurityAutoConfiguration.class
    })
    static class TestApp {
        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        IdentityClient identityClient(RestTemplate restTemplate) {
            return new IdentityClient(restTemplate, "http://identity-service:8083");
        }
    }

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private IdentityClient identityClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        // Reset the breaker between tests so each starts in CLOSED with no history.
        circuitBreakerRegistry.circuitBreaker("identityService").reset();
        reset(restTemplate);
    }

    @Test
    void downstreamFailure_runsFallback_doesNotPropagateException() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        // Must NOT throw; the fallback intercepts and returns Optional.empty().
        Optional<UUID> result = identityClient.getAnonymousId("alice@circleguard.edu");

        assertNotNull(result, "fallback must return a non-null Optional");
        assertTrue(result.isEmpty(),
                "fallback must return Optional.empty() — caller will degrade to 503");
    }

    @Test
    void repeatedFailures_openTheCircuit_andStopHittingTheDownstream() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("identityService");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState(),
                "breaker must start CLOSED");

        // Fill the sliding window (size 4) entirely with failures.
        for (int i = 0; i < 4; i++) {
            Optional<UUID> result = identityClient.getAnonymousId("alice");
            assertTrue(result.isEmpty(), "every call must fall back while breaker is CLOSED");
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                "after 4 failures (failureRate=100% ≥ threshold=50%) the breaker MUST be OPEN");

        // Once OPEN, additional calls short-circuit to the fallback without
        // ever touching RestTemplate again. We verify the downstream stays
        // protected: RestTemplate is called at most 4 times total.
        for (int i = 0; i < 10; i++) {
            assertTrue(identityClient.getAnonymousId("bob").isEmpty());
        }
        verify(restTemplate, times(4))
                .postForObject(anyString(), any(), eq(Map.class));
    }
}
