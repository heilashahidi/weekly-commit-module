package com.weeklycommit.config;

import org.springframework.stereotype.Component;

/**
 * Default {@link PrincipalResolver}, used until a user model lands (workstream B/C).
 * Returns a constant system principal. U4 contributes a {@code @Primary} JWT-backed
 * resolver that takes precedence when security is active.
 */
@Component
public class SystemPrincipalResolver implements PrincipalResolver {

    public static final String SYSTEM = "system";

    @Override
    public String currentPrincipal() {
        return SYSTEM;
    }
}
