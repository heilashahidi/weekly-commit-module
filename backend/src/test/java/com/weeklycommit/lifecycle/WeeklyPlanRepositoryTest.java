package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestPrincipalConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level coverage for {@link WeeklyPlan}: auditing inheritance, the
 * (owner, week_key) uniqueness constraint, and the derived finders backing the
 * weekly lookup (KTD 7) and the deadline backstop (U7). Runs against real
 * embedded Postgres via {@link AbstractPostgresIT}.
 */
// Roll back after each method: AbstractPostgresIT refreshes only AFTER_CLASS, so
// without this, rows from one test pollute count-sensitive assertions in the next.
@Transactional
@Import(TestPrincipalConfig.class)
class WeeklyPlanRepositoryTest extends AbstractPostgresIT {

    static final String TEST_PRINCIPAL = TestPrincipalConfig.TEST_PRINCIPAL;

    @Autowired
    WeeklyPlanRepository repository;

    @BeforeEach
    void clear() {
        // Start each test from an empty table; rolled back by @Transactional.
        repository.deleteAllInBatch();
    }

    private WeeklyPlan plan(String owner, String weekKey, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey(weekKey);
        p.setStatus(status);
        return p;
    }

    @Test
    void populatesAuditFieldsFromPrincipalOnInsert() {
        WeeklyPlan saved = repository.saveAndFlush(plan("alice", "2026-W23", PlanStatus.DRAFT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedDate()).isNotNull();
        assertThat(saved.getLastModifiedDate()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(TEST_PRINCIPAL);
        assertThat(saved.getLastModifiedBy()).isEqualTo(TEST_PRINCIPAL);
    }

    @Test
    void rejectsDuplicateOwnerAndWeekKey() {
        repository.saveAndFlush(plan("alice", "2026-W23", PlanStatus.DRAFT));

        WeeklyPlan duplicate = plan("alice", "2026-W23", PlanStatus.DRAFT);

        // (owner, week_key) unique constraint violated.
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOwnerAndWeekKeyReturnsOnlyMatchingOwnersPlan() {
        repository.saveAndFlush(plan("alice", "2026-W23", PlanStatus.DRAFT));
        repository.saveAndFlush(plan("bob", "2026-W23", PlanStatus.DRAFT));

        Optional<WeeklyPlan> found = repository.findByOwnerAndWeekKey("alice", "2026-W23");

        assertThat(found).isPresent();
        assertThat(found.get().getOwner()).isEqualTo("alice");
        assertThat(repository.findByOwnerAndWeekKey("carol", "2026-W23")).isEmpty();
    }

    @Test
    void findByStatusAndStatusDeadlineBeforeReturnsOverduePlansInThatStatus() {
        Instant now = Instant.now();
        Instant past = now.minus(1, ChronoUnit.HOURS);
        Instant future = now.plus(1, ChronoUnit.HOURS);

        WeeklyPlan overdue = plan("alice", "2026-W23", PlanStatus.DRAFT);
        overdue.setStatusDeadline(past);
        repository.saveAndFlush(overdue);

        WeeklyPlan notYetDue = plan("bob", "2026-W23", PlanStatus.DRAFT);
        notYetDue.setStatusDeadline(future);
        repository.saveAndFlush(notYetDue);

        WeeklyPlan overdueButLocked = plan("carol", "2026-W23", PlanStatus.LOCKED);
        overdueButLocked.setStatusDeadline(past);
        repository.saveAndFlush(overdueButLocked);

        List<WeeklyPlan> swept =
            repository.findByStatusAndStatusDeadlineBefore(PlanStatus.DRAFT, now);

        assertThat(swept).extracting(WeeklyPlan::getOwner).containsExactly("alice");
    }
}
