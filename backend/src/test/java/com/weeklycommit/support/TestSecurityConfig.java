package com.weeklycommit.support;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test-only {@link JwtDecoder} so the secure filter chain can be exercised
 * without a live Auth0 tenant. Decodes the literal {@code valid-token} into a
 * well-formed JWT and rejects anything else.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfig {

    public static final String VALID_TOKEN = "valid-token";
    public static final String TEST_SUBJECT = "auth0|test-user";

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!VALID_TOKEN.equals(token)) {
                throw new BadJwtException("Invalid test token");
            }
            return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(TEST_SUBJECT)
                .audience(List.of("test-audience"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        };
    }
}
