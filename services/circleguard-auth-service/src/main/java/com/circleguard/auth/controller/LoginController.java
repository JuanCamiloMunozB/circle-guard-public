package com.circleguard.auth.controller;

import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.client.IdentityClient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;
    private final MeterRegistry meterRegistry;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        System.out.println("Login attempt for user: " + username + " (pass length: " + (password != null ? password.length() : 0) + ")");

        try {
            // 1. Authenticate (Dual-Chain)
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            System.out.println("Authentication successful for: " + username);

            // 2. Anonymize (Fetch/Create Anonymous ID from Identity Service).
            //    The IdentityClient is wrapped in a Circuit Breaker: empty means
            //    identity-service is unreachable or the breaker is OPEN. We MUST
            //    NOT issue a JWT in that case — degrade to HTTP 503.
            Optional<UUID> maybeAnonymousId = identityClient.getAnonymousId(username);
            if (maybeAnonymousId.isEmpty()) {
                System.err.println("Identity service degraded — denying login for " + username);
                meterRegistry.counter("circleguard.logins", "result", "identity_degraded").increment();
                return ResponseEntity.status(503).body(Map.of(
                        "message", "Identity service temporarily unavailable, please retry"
                ));
            }
            UUID anonymousId = maybeAnonymousId.get();
            System.out.println("Anonymous ID retrieved: " + anonymousId);

            // 3. Issue Token
            String token = jwtService.generateToken(anonymousId, auth);

            meterRegistry.counter("circleguard.logins", "result", "success").increment();
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "anonymousId", anonymousId.toString()
            ));
        } catch (org.springframework.security.core.AuthenticationException e) {
            System.err.println("Authentication failed for " + username + ": " + e.getMessage());
            meterRegistry.counter("circleguard.logins", "result", "failure").increment();
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (Exception e) {
            System.err.println("Unexpected error during login for " + username + ":");
            e.printStackTrace();
            meterRegistry.counter("circleguard.logins", "result", "error").increment();
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<Map<String, String>> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        if (anonymousIdStr == null) {
            return ResponseEntity.badRequest().build();
        }
        
        UUID anonymousId = UUID.fromString(anonymousIdStr);
        
        // Create a dummy authentication for the visitor
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
