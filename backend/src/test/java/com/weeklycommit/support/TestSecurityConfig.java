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
 * without a live Auth0 tenant.
 *
 * <p>Two token forms are accepted: the bare {@code valid-token} decodes to the
 * default {@link #TEST_SUBJECT} (backward-compatible with the existing tests), and
 * {@code valid-token:<subject>} decodes to that explicit subject. The latter lets a
 * single test act as multiple principals — required for the manager-scope
 * authorization cases (workstream F), which assert that a manager, an owner, and an
 * unrelated principal are treated differently. Use {@link #tokenFor(String)} /
 * {@link #bearerFor(String)} to build them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfig {

    public static final String VALID_TOKEN = "valid-token";
    public static final String TEST_SUBJECT = "auth0|test-user";

    /** Seeded manager (V7) — manages the report subjects below. */
    public static final String MANAGER_SUBJECT = "auth0|manager-mary";
    /** A seeded direct report of {@link #MANAGER_SUBJECT}. */
    public static final String REPORT_SUBJECT = "auth0|report-ava";
    /** A principal that is neither a manager nor anyone's report. */
    public static final String OUTSIDER_SUBJECT = "auth0|outsider";

    // Separator and subject encoding use only RFC 6750 b64token-legal characters
    // ("~" and "/"); ":" and "|" are rejected by Spring's bearer-token resolver
    // before the decoder runs, so the Auth0 "auth0|sub" form is encoded as
    // "auth0/sub" inside the token and decoded back here.
    private static final String SEP = "~";

    /** A bearer token value that decodes to {@code subject}. */
    public static String tokenFor(String subject) {
        return VALID_TOKEN + SEP + subject.replace("|", "/");
    }

    /** A full {@code Authorization} header value for {@code subject}. */
    public static String bearerFor(String subject) {
        return "Bearer " + tokenFor(subject);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            String subject;
            if (VALID_TOKEN.equals(token)) {
                subject = TEST_SUBJECT;
            } else if (token != null && token.startsWith(VALID_TOKEN + SEP)) {
                subject = token.substring((VALID_TOKEN + SEP).length()).replace("/", "|");
            } else {
                throw new BadJwtException("Invalid test token");
            }
            return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(subject)
                .audience(List.of("test-audience"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        };
    }
}
