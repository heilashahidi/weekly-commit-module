package com.weeklycommit.rcdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.support.AbstractPostgresIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level coverage for {@link RcdoNode}: auditing inheritance, parent/
 * child persistence, the derived finders, and self-FK referential integrity.
 * Runs against real embedded Postgres via {@link AbstractPostgresIT}.
 */
// Roll back after each method: AbstractPostgresIT refreshes only AFTER_CLASS, so
// without this, rows from one test (e.g. roots) pollute count-sensitive assertions
// in the next.
@Transactional
@Import(RcdoNodeRepositoryTest.TestPrincipalConfig.class)
class RcdoNodeRepositoryTest extends AbstractPostgresIT {

    static final String TEST_PRINCIPAL = "rcdo-test-user";

    @TestConfiguration
    static class TestPrincipalConfig {
        @Bean
        @Primary
        PrincipalResolver testPrincipalResolver() {
            return () -> TEST_PRINCIPAL;
        }
    }

    @Autowired
    RcdoNodeRepository repository;

    private RcdoNode node(RcdoNodeType type, String title, UUID parentId) {
        RcdoNode n = new RcdoNode();
        n.setNodeType(type);
        n.setTitle(title);
        n.setParentId(parentId);
        n.setSortOrder(0);
        return n;
    }

    @Test
    void populatesAuditFieldsFromPrincipalOnInsert() {
        RcdoNode saved = repository.saveAndFlush(node(RcdoNodeType.RALLY_CRY, "Win Q3", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedDate()).isNotNull();
        assertThat(saved.getLastModifiedDate()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(TEST_PRINCIPAL);
        assertThat(saved.getLastModifiedBy()).isEqualTo(TEST_PRINCIPAL);
    }

    @Test
    void persistsAndReadsBackChildLinkedToParent() {
        RcdoNode root = repository.saveAndFlush(node(RcdoNodeType.RALLY_CRY, "Win Q3", null));
        RcdoNode child = repository.saveAndFlush(
            node(RcdoNodeType.DEFINING_OBJECTIVE, "Grow revenue", root.getId()));

        RcdoNode reloaded = repository.findById(child.getId()).orElseThrow();
        assertThat(reloaded.getParentId()).isEqualTo(root.getId());
        assertThat(reloaded.getNodeType()).isEqualTo(RcdoNodeType.DEFINING_OBJECTIVE);
    }

    @Test
    void findByParentIdIsNullReturnsOnlyRoots() {
        RcdoNode root = repository.saveAndFlush(node(RcdoNodeType.RALLY_CRY, "Win Q3", null));
        repository.saveAndFlush(node(RcdoNodeType.DEFINING_OBJECTIVE, "Grow revenue", root.getId()));

        List<RcdoNode> roots = repository.findByParentIdIsNull();

        assertThat(roots).extracting(RcdoNode::getNodeType).containsExactly(RcdoNodeType.RALLY_CRY);
    }

    @Test
    void findByNodeTypeReturnsOnlyThatLevel() {
        RcdoNode root = repository.saveAndFlush(node(RcdoNodeType.RALLY_CRY, "Win Q3", null));
        RcdoNode dobj = repository.saveAndFlush(
            node(RcdoNodeType.DEFINING_OBJECTIVE, "Grow revenue", root.getId()));
        RcdoNode outcome = repository.saveAndFlush(
            node(RcdoNodeType.OUTCOME, "ARR +20%", dobj.getId()));
        repository.saveAndFlush(
            node(RcdoNodeType.SUPPORTING_OUTCOME, "Ship billing", outcome.getId()));

        assertThat(repository.findByNodeType(RcdoNodeType.SUPPORTING_OUTCOME))
            .extracting(RcdoNode::getTitle)
            .containsExactly("Ship billing");
    }

    @Test
    void rejectsChildWithNonExistentParent() {
        RcdoNode orphan = node(RcdoNodeType.OUTCOME, "Dangling", UUID.randomUUID());

        // parent_id self-FK has no matching row -> referential integrity violation.
        assertThatThrownBy(() -> repository.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
