package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit coverage of the two-axis metrics computation (U6, R11) — no Spring
 * context, the {@link CommitmentRepository} mocked. Proves the accuracy axis is
 * computed from planned commitments only (unplanned excluded, AE6), that any
 * non-{@code DONE} planned status (including the system-set {@code UNRECONCILED})
 * counts against accuracy, the documented zero-planned convention (accuracy is
 * {@code null}, no divide-by-zero), and the planned-vs-unplanned ratio.
 */
@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    CommitmentRepository commitmentRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    PrincipalResolver principalResolver;

    private MetricsService service() {
        OwnedPlanLoader ownedPlanLoader =
            new OwnedPlanLoader(planRepository, commitmentRepository, principalResolver);
        return new MetricsService(commitmentRepository, ownedPlanLoader);
    }

    private Commitment commitment(boolean planned, ReconciliationStatus status) {
        Commitment c = new Commitment();
        c.setId(UUID.randomUUID());
        c.setWeeklyPlanId(PLAN_ID);
        c.setPlanned(planned);
        c.setReconciliationStatus(status);
        return c;
    }

    @Test
    void ae6AccuracyFromPlannedOnlyUnplannedExcluded() {
        // 4 planned (3 DONE, 1 NOT_DONE) + 2 unplanned -> accuracy 0.75 from the 4 planned only.
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of(
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, ReconciliationStatus.NOT_DONE),
            commitment(false, ReconciliationStatus.DONE),
            commitment(false, ReconciliationStatus.NOT_DONE)));

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.planId()).isEqualTo(PLAN_ID);
        assertThat(dto.plannedCount()).isEqualTo(4);
        assertThat(dto.unplannedCount()).isEqualTo(2);
        assertThat(dto.doneCount()).isEqualTo(3);
        // 3 of 4 planned DONE; the 2 unplanned (one DONE) do NOT affect accuracy.
        assertThat(dto.reconciliationAccuracy()).isEqualTo(0.75);
        // ratio = unplanned / planned = 2 / 4.
        assertThat(dto.plannedVsUnplannedRatio()).isEqualTo(0.5);
    }

    @Test
    void unreconciledPlannedCommitmentCountsAgainstAccuracy() {
        // 2 planned: 1 DONE, 1 UNRECONCILED -> accuracy 0.5 (UNRECONCILED is not done, R19).
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of(
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, ReconciliationStatus.UNRECONCILED)));

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.plannedCount()).isEqualTo(2);
        assertThat(dto.doneCount()).isEqualTo(1);
        assertThat(dto.reconciliationAccuracy()).isEqualTo(0.5);
    }

    @Test
    void nullStatusPlannedCommitmentCountsAgainstAccuracy() {
        // A still-unstatused (null) planned commitment is not done.
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of(
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, null)));

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.doneCount()).isEqualTo(1);
        assertThat(dto.reconciliationAccuracy()).isEqualTo(0.5);
    }

    @Test
    void zeroPlannedCommitmentsYieldsNullAccuracyNoDivideByZero() {
        // Only unplanned commitments: accuracy is null ("not applicable"), ratio is defined.
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of(
            commitment(false, ReconciliationStatus.DONE),
            commitment(false, ReconciliationStatus.NOT_DONE)));

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.plannedCount()).isEqualTo(0);
        assertThat(dto.unplannedCount()).isEqualTo(2);
        assertThat(dto.doneCount()).isEqualTo(0);
        assertThat(dto.reconciliationAccuracy()).isNull();
        // No planned base -> ratio 0.0 (counts still available for the consumer).
        assertThat(dto.plannedVsUnplannedRatio()).isEqualTo(0.0);
    }

    @Test
    void noCommitmentsAtAllYieldsNullAccuracyAndZeroRatio() {
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of());

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.plannedCount()).isEqualTo(0);
        assertThat(dto.unplannedCount()).isEqualTo(0);
        assertThat(dto.reconciliationAccuracy()).isNull();
        assertThat(dto.plannedVsUnplannedRatio()).isEqualTo(0.0);
    }

    @Test
    void ratioReflectsUnplannedOverPlannedCounts() {
        // 2 planned, 3 unplanned -> ratio 1.5.
        when(commitmentRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(List.of(
            commitment(true, ReconciliationStatus.DONE),
            commitment(true, ReconciliationStatus.PARTIAL),
            commitment(false, ReconciliationStatus.DONE),
            commitment(false, ReconciliationStatus.NOT_DONE),
            commitment(false, ReconciliationStatus.DROPPED)));

        PlanMetricsDto dto = service().computeMetrics(PLAN_ID);

        assertThat(dto.plannedCount()).isEqualTo(2);
        assertThat(dto.unplannedCount()).isEqualTo(3);
        assertThat(dto.plannedVsUnplannedRatio()).isEqualTo(1.5);
        // Only 1 of 2 planned DONE (PARTIAL is not done).
        assertThat(dto.reconciliationAccuracy()).isEqualTo(0.5);
    }
}
