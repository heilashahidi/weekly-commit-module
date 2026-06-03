package com.weeklycommit.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the application {@link Clock}. The lifecycle engine reads "now" from
 * this bean (never {@code Instant.now()} directly) so deadline logic — auto-lock,
 * auto-reconcile — is deterministically testable: tests inject a fixed,
 * advanceable clock instead of waiting real time.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
