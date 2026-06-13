package com.circleguard.auth.controller;

import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.client.IdentityClient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;
    private final MeterRegistry meterRegistry;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        log.debug("Processing login request");

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            log.debug("Authentication successful");

            Optional<UUID> maybeAnonymousId = identityClient.getAnonymousId(username);
            if (maybeAnonymousId.isEmpty()) {
                log.warn("Identity service degraded — denying login");
                meterRegistry.counter("circleguard.logins", "result", "identity_degraded").increment();
                return ResponseEntity.status(503).body(Map.of(
                        "message", "Identity service temporarily unavailable, please retry"
                ));
            }
            UUID anonymousId = maybeAnonymousId.get();
            log.debug("Anonymous ID retrieved");

            String token = jwtService.generateToken(anonymousId, auth);

            meterRegistry.counter("circleguard.logins", "result", "success").increment();
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "anonymousId", anonymousId.toString()
            ));
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.warn("Authentication failed: {}", e.getClass().getSimpleName());
            meterRegistry.counter("circleguard.logins", "result", "failure").increment();
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            meterRegistry.counter("circleguard.logins", "result", "error").increment();
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error"));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<Map<String, String>> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        if (anonymousIdStr == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID anonymousId = UUID.fromString(anonymousIdStr);

        Authentication visitorAuth = new UsernamePasswordAuthenticationToken(
                anonymousIdStr,
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("VISITOR"))
        );

        String token = jwtService.generateToken(anonymousId, visitorAuth);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "handoffPayload", "HANDOFF_TOKEN:" + anonymousId.toString() + ":" + token
        ));
    }
}
