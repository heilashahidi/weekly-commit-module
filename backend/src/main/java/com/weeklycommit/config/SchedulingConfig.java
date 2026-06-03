package com.weeklycommit.config;

import org.springframework.context.annotation.Configuration;
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
 * <p><b>Test interference.</b> {@code @EnableScheduling} makes the real scheduler
 * active in every profile, but the sweep's {@code fixedDelay} default is 60s — far
 * longer than any integration test runs — so it will not fire mid-test. Tests drive
 * the backstop deterministically by calling {@code DeadlineBackstopJob.sweep()}
 * directly with a controlled {@link java.time.Clock}, never relying on the scheduler.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
