package com.weeklycommit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support so the deadline backstop sweep
 * ({@code DeadlineBackstopJob}) runs periodically in production (KTD 6). Kept
 * separate from the job itself to make scheduling enablement explicit and
 * independently toggleable.
 *
 * <p><b>Single-instance assumption.</b> Plain {@code @Scheduled} fires on every
 * running instance; multi-instance safety (ShedLock/Quartz) is deferred per Scope
 * Boundaries. For a single-instance build this is correct.
 *
 * <p><b>Test isolation.</b> This config is gated {@code @Profile("!test")} so the
 * real scheduler is never active under the {@code test} profile — otherwise the
 * production 60s sweep would fire mid-IT against the shared test DB and race the
 * deterministic test fixtures. Tests drive the backstop by calling
 * {@code DeadlineBackstopJob.sweep()} directly with a controlled
 * {@link java.time.Clock}, so they never need {@code @EnableScheduling} active.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
