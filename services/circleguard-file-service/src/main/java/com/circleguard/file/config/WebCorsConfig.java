package com.circleguard.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Centralized CORS policy (External Configuration pattern). The allowed origins
 * are injected from {@code cors.allowed-origins} (relaxed-bound env
 * {@code CORS_ALLOWED_ORIGINS}) with a non-production local default, replacing
 * the previous wildcard {@code @CrossOrigin(origins = "*")} on controllers.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfig(
            @Value("${cors.allowed-origins:http://localhost:8081,http://localhost:8080}") String allowedOrigins) {
        // Split on a literal comma (no regex quantifiers) and trim each entry.
        // Avoids the catastrophic-backtracking risk of "\\s*,\\s*" on untrusted
        // configuration while still tolerating whitespace around the separator.
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
