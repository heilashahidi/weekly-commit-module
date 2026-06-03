package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtPrincipalResolverTest {

    private final JwtPrincipalResolver resolver = new JwtPrincipalResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSystemWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThat(resolver.currentPrincipal()).isEqualTo(SystemPrincipalResolver.SYSTEM);
    }

    @Test
    void returnsSystemForAnonymousAuthentication() {
        var anonymous = new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        assertThat(resolver.currentPrincipal()).isEqualTo(SystemPrincipalResolver.SYSTEM);
    }

    @Test
    void returnsSubjectWhenAuthenticated() {
        var auth = new UsernamePasswordAuthenticationToken(
            "auth0|abc123", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_read")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(resolver.currentPrincipal()).isEqualTo("auth0|abc123");
    }
}
