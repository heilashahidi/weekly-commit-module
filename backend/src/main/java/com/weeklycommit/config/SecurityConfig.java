package com.weeklycommit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration.
 *
 * <ul>
 *   <li>Default (non-dev) profile: stateless OAuth2 resource server. {@code /health}
 *       is public; everything else requires a valid Auth0 JWT.</li>
 *   <li>{@code dev} profile: authentication relaxed for local development
 *       (guarded by {@link DevProfileGuard}).</li>
 * </ul>
 *
 * The {@link JwtDecoder} enforces both issuer and audience. It is only created
 * when {@code wc.auth.issuer-uri} is configured, so tests can supply their own
 * decoder without reaching Auth0.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String HEALTH_PATH = "/health";

    @Bean
    @Profile("!dev")
    SecurityFilterChain secureFilterChain(HttpSecurity http) throws Exception {
        // .cors(withDefaults()) picks up the bean named "corsConfigurationSource".
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, HEALTH_PATH).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }));
        return http.build();
    }

    @Bean
    @Profile("dev")
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Real Auth0-backed decoder, enforcing issuer + audience. Only instantiated
     * when {@code wc.auth.issuer-uri} is set (i.e. a tenant is configured).
     */
    @Bean
    @Profile("!dev")
    @ConditionalOnProperty(prefix = "wc.auth", name = "issuer-uri")
    JwtDecoder auth0JwtDecoder(
            @Value("${wc.auth.issuer-uri}") String issuerUri,
            @Value("${wc.auth.audience}") String audience) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience =
            new DelegatingOAuth2TokenValidator<>(withIssuer, new AudienceValidator(audience));
        decoder.setJwtValidator(withAudience);
        return decoder;
    }
}
