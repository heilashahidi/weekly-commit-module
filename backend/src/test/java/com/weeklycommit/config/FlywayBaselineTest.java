package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.support.AbstractPostgresIT;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Confirms the Flyway baseline actually ran (not a vacuous empty migration):
 * the V1 baseline must be applied exactly once.
 */
class FlywayBaselineTest extends AbstractPostgresIT {

    @Autowired
    Flyway flyway;

    @Test
    void baselineMigrationAppliedExactlyOnce() {
        MigrationInfo[] applied = flyway.info().applied();

        long baselineApplications = Arrays.stream(applied)
            .filter(info -> info.getVersion() != null)
            .filter(info -> "1".equals(info.getVersion().getVersion()))
            .count();

        assertThat(baselineApplications).isEqualTo(1L);
    }
}
