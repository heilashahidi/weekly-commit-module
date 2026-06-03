package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("expected-api");

    private Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("subject")
            .audience(audience)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }

    @Test
    void acceptsTokenCarryingRequiredAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("expected-api"))).hasErrors())
            .isFalse();
    }

    @Test
    void rejectsTokenMissingRequiredAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("some-other-api"))).hasErrors())
            .isTrue();
    }
}
