package com.weeklycommit.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the auditing principal from the security context (the JWT subject).
 * Falls back to {@link SystemPrincipalResolver#SYSTEM} for unauthenticated
 * contexts (background jobs, the relaxed dev profile, system operations).
 *
 * <p>This is the indirection point the auditing layer depends on: workstream B/C
 * can replace this with a resolver that maps the JWT subject to a user record
 * without touching {@code AuditorAwareImpl} or any entity.
 */
@Component
public class JwtPrincipalResolver implements PrincipalResolver {

    @Override
    public String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return SystemPrincipalResolver.SYSTEM;
        }
        return authentication.getName();
    }
}
