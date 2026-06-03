package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestPrincipalConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level coverage for {@link Commitment}: auditing inheritance, the
 * weekly_plan and rcdo_node FKs (KTD 3 link integrity, both directions), the
 * planned/unplanned finder (R7, R8), and carry-forward lineage (R14). Runs
 * against real embedded Postgres via {@link AbstractPostgresIT}.
 */
// Roll back after each method: AbstractPostgresIT refreshes only AFTER_CLASS, so
// without this, rows from one test pollute count-sensitive assertions in the next.
// Only the lifecycle tables are cleared — the V3 rcdo_node seed (referenced by the
// commitment FK) must remain.
@Transactional
@Import(TestPrincipalConfig.class)
class CommitmentRepositoryTest extends AbstractPostgresIT {

    static final String TEST_PRINCIPAL = TestPrincipalConfig.TEST_PRINCIPAL;

    // A SUPPORTING_OUTCOME seeded by V3__rcdo_seed.sql with a fixed UUID.
    static final UUID SEEDED_RCDO_NODE_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Autowired
    CommitmentRepository repository;

    @Autowired
    WeeklyPlanRepository planRepository;

    @BeforeEach
    void clear() {
        // Clear only the lifecycle tables (child first); rolled back by @Transactional.
        // Do NOT touch rcdo_node — the seed rows are FK targets these tests depend on.
        repository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
    }

    private WeeklyPlan savedPlan(String owner) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey("2026-W23");
        p.setStatus(PlanStatus.DRAFT);
        return planRepository.saveAndFlush(p);
    }

    private Commitment commitment(UUID planId, UUID rcdoNodeId, String title, boolean planned) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(rcdoNodeId);
        c.setTitle(title);
        c.setPlanned(planned);
        return c;
    }

    @Test
    void persistsAndReadsBackWithValidFksAndAuditFields() {
        WeeklyPlan plan = savedPlan("alice");

        Commitment saved = repository.saveAndFlush(
            commitment(plan.getId(), SEEDED_RCDO_NODE_ID, "Ship billing slice", true));

        Commitment reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWeeklyPlanId()).isEqualTo(plan.getId());
        assertThat(reloaded.getRcdoNodeId()).isEqualTo(SEEDED_RCDO_NODE_ID);
        assertThat(reloaded.isPlanned()).isTrue();
        assertThat(reloaded.getReconciliationStatus()).isNull();
        assertThat(reloaded.getCarryWeekCount()).isZero();
        assertThat(reloaded.getCreatedBy()).isEqualTo(TEST_PRINCIPAL);
        assertThat(reloaded.getLastModifiedBy()).isEqualTo(TEST_PRINCIPAL);
        assertThat(reloaded.getCreatedDate()).isNotNull();
        assertThat(reloaded.getLastModifiedDate()).isNotNull();
    }

    @Test
    void rejectsCommitmentWithNonExistentRcdoNode() {
        WeeklyPlan plan = savedPlan("alice");

        Commitment orphan =
            commitment(plan.getId(), UUID.randomUUID(), "Dangling link", true);

        // rcdo_node_id FK has no matching row -> referential integrity violation (KTD 3).
        assertThatThrownBy(() -> repository.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCommitmentWithNonExistentWeeklyPlan() {
        Commitment orphan =
            commitment(UUID.randomUUID(), SEEDED_RCDO_NODE_ID, "No plan", true);

        // weekly_plan_id FK has no matching row -> referential integrity violation.
        assertThatThrownBy(() -> repository.saveAndFlush(orphan))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByWeeklyPlanIdAndPlannedReturnsOnlyPlannedCommitments() {
        WeeklyPlan plan = savedPlan("alice");
        repository.saveAndFlush(
            commitment(plan.getId(), SEEDED_RCDO_NODE_ID, "Planned in draft", true));
        repository.saveAndFlush(
            commitment(plan.getId(), SEEDED_RCDO_NODE_ID, "Unplanned add", false));

        List<Commitment> planned =
            repository.findByWeeklyPlanIdAndPlanned(plan.getId(), true);

        assertThat(planned)
            .extracting(Commitment::getTitle)
            .containsExactly("Planned in draft");
        assertThat(repository.findByWeeklyPlanId(plan.getId())).hasSize(2);
    }

    @Test
    void persistsCarryLineage() {
        WeeklyPlan plan = savedPlan("alice");
        Commitment source = repository.saveAndFlush(
            commitment(plan.getId(), SEEDED_RCDO_NODE_ID, "Original commitment", true));

        Commitment carried =
            commitment(plan.getId(), SEEDED_RCDO_NODE_ID, "Carried commitment", true);
        carried.setCarriedFromId(source.getId());
        carried.setCarryWeekCount(2);
        Commitment savedCarried = repository.saveAndFlush(carried);

        Commitment reloaded = repository.findById(savedCarried.getId()).orElseThrow();
        assertThat(reloaded.getCarriedFromId()).isEqualTo(source.getId());
        assertThat(reloaded.getCarryWeekCount()).isEqualTo(2);
    }
}
