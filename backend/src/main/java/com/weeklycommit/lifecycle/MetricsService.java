package com.weeklycommit.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the two-axis reconciliation metrics for a weekly plan (U6, R11). Pure
 * in-memory aggregation over a single {@link CommitmentRepository#findByWeeklyPlanId}
 * query — mirroring {@code RcdoService.getTree}'s assembly — so the computation is
 * unit-testable without HTTP.
 *
 * <p><b>Axis 1 — reconciliation accuracy (planned only, R11).</b> Of the
 * <i>planned</i> commitments, the fraction marked {@code DONE}. Unplanned
 * commitments are excluded from both numerator and denominator. Every non-{@code DONE}
 * planned status counts against accuracy: {@code PARTIAL}, {@code NOT_DONE},
 * {@code DROPPED}, the system-set {@code UNRECONCILED}, and a still-null status all
 * count as "not done" — only {@code DONE} is in the numerator. With zero planned
 * commitments the accuracy is {@code null} ("not applicable", no planned work to be
 * accurate about) rather than a divide-by-zero or a misleading 0.0.
 *
 * <p><b>Axis 2 — planned-vs-unplanned ratio (reactive signal, R11).</b> Surfaced as
 * both raw counts and a ratio = unplanned / planned (direction: unplanned per
 * planned). With zero planned commitments the ratio is {@code 0.0} (undefined base);
 * the raw counts remain available for the consumer.
 *
 * <p><b>Ownership.</b> {@link #getMetrics(UUID)} (the HTTP entry point) requires the
 * plan to belong to the current principal — 404 if missing, 403 on mismatch —
 * matching the other lifecycle services. {@link #computeMetrics(UUID)} is the pure
 * computation seam with no ownership check, used by the controller after ownership is
 * established and directly by unit tests.
 */
@Service
public class MetricsService {

    private final CommitmentRepository commitmentRepository;
    private final OwnedPlanLoader ownedPlanLoader;

    public MetricsService(
            CommitmentRepository commitmentRepository,
            OwnedPlanLoader ownedPlanLoader) {
        this.commitmentRepository = commitmentRepository;
        this.ownedPlanLoader = ownedPlanLoader;
    }

    /**
     * Ownership-checked metrics for the HTTP layer: 404 if the plan does not exist,
     * 403 if it belongs to another principal, then the computed {@link PlanMetricsDto}.
     */
    @Transactional(readOnly = true)
    public PlanMetricsDto getMetrics(UUID planId) {
        ownedPlanLoader.loadOwned(planId);
        return computeMetrics(planId);
    }

    /**
     * Pure two-axis computation over a plan's commitments (no ownership check). Loads
     * the commitments once and aggregates in memory.
     */
    @Transactional(readOnly = true)
    public PlanMetricsDto computeMetrics(UUID planId) {
        List<Commitment> commitments = commitmentRepository.findByWeeklyPlanId(planId);

        long plannedCount = commitments.stream().filter(Commitment::isPlanned).count();
        long unplannedCount = commitments.size() - plannedCount;
        long doneCount = commitments.stream()
            .filter(Commitment::isPlanned)
            .filter(c -> c.getReconciliationStatus() == ReconciliationStatus.DONE)
            .count();

        // Accuracy is "not applicable" (null) with no planned work, never a divide-by-zero.
        Double accuracy = plannedCount == 0 ? null : (double) doneCount / plannedCount;
        // Ratio: unplanned per planned; 0.0 when there is no planned base.
        double ratio = plannedCount == 0 ? 0.0 : (double) unplannedCount / plannedCount;

        return new PlanMetricsDto(
            planId, plannedCount, unplannedCount, doneCount, accuracy, ratio);
    }
}
