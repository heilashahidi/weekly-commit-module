package com.weeklycommit.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for the auth-relaxing {@code dev} profile.
 *
 * <p>The {@code dev} profile disables authentication for local convenience. To
 * ensure it can never run in a deployed environment, the application refuses to
 * start when the {@code dev} profile is active without an explicit local marker
 * ({@code WC_LOCAL_DEV=true}).
 */
@Component
public class DevProfileGuard {

    static final String DEV_PROFILE = "dev";
    static final String LOCAL_MARKER_PROPERTY = "wc.local-dev";
    static final String LOCAL_MARKER_ENV = "WC_LOCAL_DEV";

    private final Environment environment;

    public DevProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verifyDevProfileIsLocal() {
        boolean devActive = Arrays.asList(environment.getActiveProfiles()).contains(DEV_PROFILE);
        if (!devActive) {
            return;
        }
        if (!localMarkerPresent()) {
            throw new IllegalStateException(
                "The 'dev' profile relaxes authentication and must not run outside local "
                + "development. Set " + LOCAL_MARKER_ENV + "=true to confirm a local environment.");
        }
    }

    private boolean localMarkerPresent() {
        String marker = environment.getProperty(LOCAL_MARKER_PROPERTY);
        if (marker == null) {
            marker = System.getenv(LOCAL_MARKER_ENV);
        }
        return Boolean.parseBoolean(marker);
    }
}
