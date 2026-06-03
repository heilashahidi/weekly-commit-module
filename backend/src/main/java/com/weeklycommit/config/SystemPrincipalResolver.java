package com.weeklycommit.config;

/**
 * Holds the system-principal constant and the unauthenticated fallback used for
 * auditing. Once a user model lands (workstream B/C), {@link JwtPrincipalResolver}
 * can be swapped to resolve a mapped user record instead of the raw JWT subject.
 *
 * <p>Not a bean: the active {@link PrincipalResolver} is {@link JwtPrincipalResolver},
 * which falls back to {@link #SYSTEM} when no authenticated principal is present.
 */
public final class SystemPrincipalResolver {

    public static final String SYSTEM = "system";

    private SystemPrincipalResolver() {
    }
}
