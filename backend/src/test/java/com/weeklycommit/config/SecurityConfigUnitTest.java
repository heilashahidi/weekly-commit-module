package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Covers the fail-fast guards in {@link SecurityConfig} — the logic the JaCoCo
 * {@code *Config} exclusion previously hid. Both guards run before any web wiring,
 * so they are exercised with a mocked {@link HttpSecurity} that is never touched.
 */
class SecurityConfigUnitTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    @SuppressWarnings("unchecked")
    void secureChainFailsFastWhenNoJwtDecoder() {
        ObjectProvider<JwtDecoder> noDecoder = mock(ObjectProvider.class);
        when(noDecoder.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> config.secureFilterChain(mock(HttpSecurity.class), noDecoder))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No JwtDecoder");
    }

    @Test
    void auth0DecoderRejectsMissingAudience() {
        // Audience is validated before the issuer is contacted, so no network call.
        assertThatThrownBy(() -> config.auth0JwtDecoder("https://tenant.auth0.com/", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("audience");
    }
}
