package com.weeklycommit.config;

/**
 * Resolves the identifier of the current principal for auditing.
 *
 * <p>Indirection point: {@code AuditorAwareImpl} delegates here so the source of
 * the principal can change without touching the auditing wiring or any entity.
 * The foundation ships a system default; U4 contributes a JWT-backed resolver,
 * and workstream B/C can swap to a mapped user record — a one-class change.
 */
public interface PrincipalResolver {

    String currentPrincipal();
}
