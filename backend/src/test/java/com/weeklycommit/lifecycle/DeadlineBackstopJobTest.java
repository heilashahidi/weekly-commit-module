package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.lifecycle.support.MutableClock;
import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestPrincipalConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deadline-backstop coverage (U7, KTD 6, R18/R19/R4/R5). Drives time through a
 * single {@link MutableClock} the test controls (imported as the {@code @Primary}
 * {@link Clock}, so both {@link LifecycleService} and {@link DeadlineBackstopJob}
 * inject it), sets each plan's {@code statusDeadline} to a known instant, advances
 * the clock past it, calls {@link DeadlineBackstopJob#sweep()} directly (never
 * relying on the scheduler firing), and asserts the auto-transition.
 *
 * <p>Covers AE1 (auto-lock with provenance {@code AUTO_LOCKED}), AE2 (empty draft
 * auto-locks {@code no_plan=true}), the {@code LOCKED -> RECONCILING} edge, and AE9
 * (auto-close fills {@code UNRECONCILED} — never {@code DONE} — and leaves IC-set
 * statuses untouched). Also asserts idempotency: a plan not past its deadline and an
 * already-{@code RECONCILED} plan are left untouched.
 *
 * <p>Runs against real embedded Postgres via {@link AbstractPostgresIT}.
 */
// Roll back after each method: AbstractPostgresIT refreshes only AFTER_CLASS, so
// without this, rows from one test pollute the finder-based assertions in the next.
// Only the lifecycle tables are cleared — the V3 rcdo_node seed (commitment FK
// target) must remain.
@Transactional
@Import({TestPrincipalConfig.class, DeadlineBackstopJobTest.FixedClockConfig.class})
class DeadlineBackstopJobTest extends AbstractPostgresIT {

    // 2026-06-03T12:00:00Z; the test advances this clock past each plan's deadline.
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    // A SUPPORTING_OUTCOME seeded by V3__rcdo_seed.sql with a fixed UUID.
    private static final UUID SEEDED_RCDO_NODE_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444441");

    /**
     * Supplies the single mutable Clock both the service and the job share. It is
     * declared as the {@code @Primary} {@link Clock} (overriding the production
     * {@code systemClock}) so every {@code Clock} injection point gets it; the test
     * reaches the same instance by its concrete {@link MutableClock} type.
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    DeadlineBackstopJob job;

    @Autowired
    Clock clockBean;

    private MutableClock clock;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @BeforeEach
    void reset() {
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        clock = (MutableClock) clockBean;
        clock.setInstant(NOW);
    }

    private WeeklyPlan savedPlan(String owner, PlanStatus status, Instant deadline) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey("2026-W23");
        p.setStatus(status);
        p.setStatusDeadline(deadline);
        return planRepository.saveAndFlush(p);
    }

    private Commitment savedCommitment(UUID planId, ReconciliationStatus status) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(SEEDED_RCDO_NODE_ID);
        c.setTitle("Ship billing slice");
        c.setPlanned(true);
        c.setReconciliationStatus(status);
        return commitmentRepository.saveAndFlush(c);
    }

    // --- AE1: overdue DRAFT with commitments auto-locks (AUTO_LOCKED) ---

    @Test
    void sweepAutoLocksOverdueDraftWithCommitmentsAndStampsAutoLocked() {
        WeeklyPlan plan =
            savedPlan("alice", PlanStatus.DRAFT, NOW.plus(Duration.ofHours(1)));
        savedCommitment(plan.getId(), null);
        savedCommitment(plan.getId(), null);
        clock.advance(Duration.ofHours(2));

        job.sweep();

        WeeklyPlan swept = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PlanStatus.LOCKED);
        assertThat(swept.getLockType()).isEqualTo(LockType.AUTO_LOCKED);
        assertThat(swept.isNoPlan()).isFalse();
        // deadline advanced to the next edge, so it is not re-swept on the same pass.
        assertThat(swept.getStatusDeadline()).isAfter(clock.instant());
    }

    // --- AE2: overdue empty DRAFT auto-locks with no_plan=true ---

    @Test
    void sweepAutoLocksOverdueEmptyDraftWithNoPlanTrue() {
        WeeklyPlan plan =
            savedPlan("bob", PlanStatus.DRAFT, NOW.plus(Duration.ofHours(1)));
        clock.advance(Duration.ofHours(2));

        job.sweep();

        WeeklyPlan swept = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PlanStatus.LOCKED);
        assertThat(swept.getLockType()).isEqualTo(LockType.AUTO_LOCKED);
        assertThat(swept.isNoPlan()).isTrue();
    }

    // --- LOCKED -> RECONCILING edge (R18) ---

    @Test
    void sweepAdvancesOverdueLockedToReconciling() {
        WeeklyPlan plan =
            savedPlan("carol", PlanStatus.LOCKED, NOW.plus(Duration.ofHours(1)));
        clock.advance(Duration.ofHours(2));

        job.sweep();

        WeeklyPlan swept = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PlanStatus.RECONCILING);
        assertThat(swept.getStatusDeadline()).isAfter(clock.instant());
    }

    // --- AE9: auto-close fills UNRECONCILED, never DONE; IC statuses untouched ---

    @Test
    void sweepAutoClosesOverdueReconcilingFillingUnreconciledOnly() {
        WeeklyPlan plan =
            savedPlan("dave", PlanStatus.RECONCILING, NOW.plus(Duration.ofHours(1)));
        Commitment done = savedCommitment(plan.getId(), ReconciliationStatus.DONE);
        Commitment partial = savedCommitment(plan.getId(), ReconciliationStatus.PARTIAL);
        Commitment unstatusedA = savedCommitment(plan.getId(), null);
        Commitment unstatusedB = savedCommitment(plan.getId(), null);
        clock.advance(Duration.ofHours(2));

        job.sweep();

        WeeklyPlan swept = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PlanStatus.RECONCILED);
        assertThat(swept.getStatusDeadline()).isNull();

        // The two null commitments become UNRECONCILED (never DONE).
        assertThat(commitmentRepository.findById(unstatusedA.getId()).orElseThrow()
                .getReconciliationStatus())
            .isEqualTo(ReconciliationStatus.UNRECONCILED);
        assertThat(commitmentRepository.findById(unstatusedB.getId()).orElseThrow()
                .getReconciliationStatus())
            .isEqualTo(ReconciliationStatus.UNRECONCILED);

        // The IC-set statuses are left exactly as they were.
        assertThat(commitmentRepository.findById(done.getId()).orElseThrow()
                .getReconciliationStatus())
            .isEqualTo(ReconciliationStatus.DONE);
        assertThat(commitmentRepository.findById(partial.getId()).orElseThrow()
                .getReconciliationStatus())
            .isEqualTo(ReconciliationStatus.PARTIAL);
    }

    // --- idempotency / safety ---

    @Test
    void sweepLeavesPlanNotPastItsDeadlineUntouched() {
        WeeklyPlan plan =
            savedPlan("erin", PlanStatus.DRAFT, NOW.plus(Duration.ofDays(2)));
        clock.advance(Duration.ofHours(2));

        job.sweep();

        WeeklyPlan after = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(after.getLockType()).isNull();
    }

    @Test
    void sweepIgnoresAlreadyReconciledPlan() {
        // RECONCILED is terminal: its statusDeadline is null, so the finder
        // (statusDeadline < now) never returns it.
        WeeklyPlan plan = savedPlan("frank", PlanStatus.RECONCILED, null);
        clock.advance(Duration.ofDays(10));

        job.sweep();

        WeeklyPlan after = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PlanStatus.RECONCILED);
    }

    @Test
    void sweepIsNoOpWhenNothingOverdue() {
        List<WeeklyPlan> none = planRepository.findAll();
        assertThat(none).isEmpty();

        job.sweep();

        assertThat(planRepository.findAll()).isEmpty();
    }
}
