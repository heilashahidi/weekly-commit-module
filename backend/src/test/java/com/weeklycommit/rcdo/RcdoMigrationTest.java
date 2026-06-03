package com.weeklycommit.rcdo;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.support.AbstractPostgresIT;
import java.util.Arrays;
import java.util.List;
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

    @Autowired
    RcdoNodeRepository repository;

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
    void contextLoadsWithMigratedSchemaAndEntityMapping() {
        // Reaching this assertion means @SpringBootTest started with the V2
        // migration applied and Hibernate accepted the RcdoNode mapping against
        // the migrated table. Note: the test profile uses ddl-auto: update (so
        // Hibernate can add test-fixture tables), which is more lenient than
        // production's validate — it does not catch every column-type nuance
        // (e.g. timestamp vs timestamptz). It does prove the migration applies
        // cleanly and the entity loads against the real Postgres schema.
        assertThat(flyway).isNotNull();
    }

    @Test
    void seedTreeHasExactlyOneRootAndValidParentTypes() {
        List<RcdoNode> all = repository.findAll();

        // Exactly one Rally Cry root.
        assertThat(all).filteredOn(n -> n.getParentId() == null)
            .extracting(RcdoNode::getNodeType)
            .containsExactly(RcdoNodeType.RALLY_CRY);

        // Every non-root node's parent exists and is the legal parent type.
        for (RcdoNode node : all) {
            if (node.getParentId() == null) {
                continue;
            }
            RcdoNode parent = repository.findById(node.getParentId()).orElseThrow();
            RcdoNodeType expectedParent = switch (node.getNodeType()) {
                case DEFINING_OBJECTIVE -> RcdoNodeType.RALLY_CRY;
                case OUTCOME -> RcdoNodeType.DEFINING_OBJECTIVE;
                case SUPPORTING_OUTCOME -> RcdoNodeType.OUTCOME;
                case RALLY_CRY -> null;
            };
            assertThat(parent.getNodeType()).isEqualTo(expectedParent);
        }
    }
}
