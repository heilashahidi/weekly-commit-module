package com.weeklycommit.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.support.AbstractPostgresIT;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Confirms the reporting-mapping migration (V7) applied and that the entity
 * mapping matches the migration. Context load under {@code ddl-auto: validate}
 * (via {@link AbstractPostgresIT}) proves ReportingEdge and V7 agree; the seed
 * assertions prove the manager -> report edges and display names are resolvable so
 * the manager-scope authorization join (U2) and roll-up (U3) have data to act on.
 */
class ReportingMigrationTest extends AbstractPostgresIT {

    private static final String MANAGER = "auth0|manager-mary";

    @Autowired
    Flyway flyway;

    @Autowired
    ReportingRepository repository;

    @Test
    void reportingMigrationAppliedExactlyOnce() {
        MigrationInfo[] applied = flyway.info().applied();

        long v7Applications = Arrays.stream(applied)
            .filter(info -> info.getVersion() != null)
            .filter(info -> "7".equals(info.getVersion().getVersion()))
            .count();

        assertThat(v7Applications).isEqualTo(1L);
    }

    @Test
    void seededManagerResolvesReportsWithDisplayNames() {
        var reports = repository.findByManagerSub(MANAGER);

        assertThat(reports).hasSize(4);
        assertThat(reports)
            .allSatisfy(edge -> {
                assertThat(edge.getReportSub()).startsWith("auth0|report-");
                assertThat(edge.getReportDisplayName()).isNotBlank();
            });
    }

    @Test
    void authorizationPredicateMatchesSeededEdgesOnly() {
        assertThat(repository.existsByManagerSubAndReportSub(MANAGER, "auth0|report-ava")).isTrue();
        assertThat(repository.existsByManagerSubAndReportSub(MANAGER, "auth0|not-a-report")).isFalse();
        assertThat(repository.existsByManagerSub(MANAGER)).isTrue();
        assertThat(repository.existsByManagerSub("auth0|not-a-manager")).isFalse();
    }
}
