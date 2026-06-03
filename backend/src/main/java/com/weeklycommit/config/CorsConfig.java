package com.weeklycommit.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS policy: an explicit origin allowlist (never wildcard), so credentialed
 * cross-origin requests are accepted only from known frontends — the standalone
 * dev origin and, in production, the PA host shell origin (supplied via env).
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${wc.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
        // allowCredentials(true) with a wildcard origin is rejected by the spec
        // at request time, but a misconfigured WC_CORS_ALLOWED_ORIGINS="*" would
        // only surface as broken CORS in the browser. Fail fast at startup instead.
        if (origins.contains("*")) {
            throw new IllegalStateException(
                "wc.cors.allowed-origins must be an explicit allowlist: a wildcard '*' is "
                + "incompatible with credentialed requests. List each frontend origin.");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
