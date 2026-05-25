package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client for identity-service. Wrapped with a Resilience4j Circuit Breaker so that
 * a downstream outage degrades gracefully (Optional.empty) instead of cascading
 * 5xx errors to the login endpoint.
 *
 * Circuit Breaker thresholds live in application.yml under
 * resilience4j.circuitbreaker.instances.identityService (parameterized via env vars).
 */
@Component
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);
    private static final String CB_NAME = "identityService";

    private final RestTemplate restTemplate;
    private final String identityBaseUrl;

    public IdentityClient(@Value("${IDENTITY_API_URL:http://localhost:8083}") String identityBaseUrl) {
        this(new RestTemplate(), identityBaseUrl);
    }

    /**
     * Visible-for-testing constructor: lets unit and integration tests inject a
     * stub or real RestTemplate together with the target URL of identity-service.
     * Public so test classes outside the {@code client} package (e.g.
     * {@code com.circleguard.auth.integration.*}) can use it.
     */
    public IdentityClient(RestTemplate restTemplate, String identityBaseUrl) {
        this.restTemplate = restTemplate;
        this.identityBaseUrl = identityBaseUrl;
    }

    /**
     * Resolves the anonymous UUID for a real identity. Returns Optional.empty()
     * when identity-service is unreachable or the Circuit Breaker is OPEN —
     * the caller MUST treat that as a degraded-service condition (HTTP 503),
     * never as an authentication success.
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackGetAnonymousId")
    @SuppressWarnings("unchecked")
    public Optional<UUID> getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map<String, Object> response = restTemplate.postForObject(
                identityBaseUrl + "/api/v1/identities/map",
                request,
                Map.class
        );
        if (response == null || response.get("anonymousId") == null) {
            // Treat a malformed response as a transient failure so the CB can react.
            throw new IllegalStateException("identity-service returned no anonymousId");
        }
        return Optional.of(UUID.fromString(response.get("anonymousId").toString()));
    }

    /**
     * Fallback invoked by Resilience4j when getAnonymousId fails (or the
     * circuit is OPEN). Signature MUST match the protected method + a trailing
     * Throwable. We deliberately do NOT rethrow: empty is the controlled
     * "degraded" answer.
     */
    @SuppressWarnings("unused")
    private Optional<UUID> fallbackGetAnonymousId(String realIdentity, Throwable cause) {
        log.warn("identity-service unavailable, returning empty anonymousId (cause: {})",
                cause.getClass().getSimpleName());
        return Optional.empty();
    }
}
