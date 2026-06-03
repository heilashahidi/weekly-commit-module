package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    private final CorsConfig config = new CorsConfig();

    @Test
    void buildsAllowlistFromCommaSeparatedOrigins() {
        CorsConfigurationSource source = config.corsConfigurationSource(
            "https://app.example.com, https://pa.example.com");

        HttpServletRequest request = new MockHttpServletRequest();
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
            .containsExactly("https://app.example.com", "https://pa.example.com");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void rejectsWildcardOriginWithCredentials() {
        assertThatThrownBy(() -> config.corsConfigurationSource("*"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wildcard");
    }
}
