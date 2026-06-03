package com.weeklycommit.rcdo;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.support.AbstractPostgresIT;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Confirms the RCDO schema migration applied and that the entity mapping matches
 * the migration. The mere fact that the Spring context loads under
 * {@code ddl-auto: validate} (via {@link AbstractPostgresIT}) proves RcdoNode and
 * V2__rcdo_tables.sql agree on columns, types, and nullability — the primary
 * entity/migration drift guard.
 */
class RcdoMigrationTest extends AbstractPostgresIT {

    @Autowired
    Flyway flyway;

    @Test
    void rcdoTableMigrationAppliedExactlyOnce() {
        MigrationInfo[] applied = flyway.info().applied();

        long v2Applications = Arrays.stream(applied)
            .filter(info -> info.getVersion() != null)
            .filter(info -> "2".equals(info.getVersion().getVersion()))
            .count();

        assertThat(v2Applications).isEqualTo(1L);
    }

    @Test
    void contextLoadsUnderValidateProvingEntityMatchesSchema() {
        // Reaching this assertion means @SpringBootTest started with
        // ddl-auto: validate and Hibernate accepted the RcdoNode mapping against
        // the migrated table. A column/type/nullability mismatch would have
        // failed context startup before this point.
        assertThat(flyway).isNotNull();
    }
}
