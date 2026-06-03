package com.weeklycommit.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestPrincipalConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Verifies Spring Data JPA auditing populates the {@link AbstractAuditingEntity}
 * fields, and that {@code createdBy}/{@code lastModifiedBy} flow from the
 * {@link PrincipalResolver}.
 */
@Import(TestPrincipalConfig.class)
class AbstractAuditingEntityTest extends AbstractPostgresIT {

    static final String TEST_PRINCIPAL = TestPrincipalConfig.TEST_PRINCIPAL;

    @Autowired
    TestAuditEntityRepository repository;

    @Test
    void populatesAuditFieldsAndPrincipalOnInsert() {
        TestAuditEntity entity = new TestAuditEntity();
        entity.setName("first");

        TestAuditEntity saved = repository.saveAndFlush(entity);

        assertThat(saved.getCreatedDate()).isNotNull();
        assertThat(saved.getLastModifiedDate()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(TEST_PRINCIPAL);
        assertThat(saved.getLastModifiedBy()).isEqualTo(TEST_PRINCIPAL);
    }

    @Test
    void createdDateStableAcrossUpdate() throws InterruptedException {
        TestAuditEntity entity = new TestAuditEntity();
        entity.setName("orig");
        TestAuditEntity saved = repository.saveAndFlush(entity);
        Instant createdAt = saved.getCreatedDate();
        Instant firstModified = saved.getLastModifiedDate();

        Thread.sleep(10);
        saved.setName("updated");
        TestAuditEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.getCreatedDate()).isEqualTo(createdAt);
        assertThat(updated.getLastModifiedDate()).isAfterOrEqualTo(firstModified);
    }
}
