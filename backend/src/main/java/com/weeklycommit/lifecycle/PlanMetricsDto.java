package com.weeklycommit.lifecycle;

import java.util.UUID;

/**
 * The two-axis reconciliation signals for a single {@link WeeklyPlan} (U6, R11),
 * computed by {@link MetricsService} purely from the plan's commitments. A record
 * (not the entity) so the response stays a clean, stable shape for workstream F's
 * manager dashboard — mirroring {@code WeeklyPlanDto}/{@code CommitmentDto}.
 *
 * <p><b>Axis 1 — reconciliation accuracy (planned only).</b>
 * {@link #reconciliationAccuracy} = {@link #doneCount} / {@link #plannedCount}: the
 * fraction of <i>planned</i> commitments marked {@code DONE}. Unplanned commitments
 * are excluded entirely (R11), and every non-{@code DONE} planned status — including
 * the system-set {@code UNRECONCILED} and a still-null status — counts against
 * accuracy. It is a nullable {@link Double}: {@code null} means "not applicable"
 * when {@link #plannedCount} is zero (no planned work to be accurate about), so the
 * consumer can distinguish "perfectly inaccurate" (0.0) from "nothing to measure".
 *
 * <p><b>Axis 2 — planned-vs-unplanned ratio (reactive signal).</b> Both raw counts
 * ({@link #plannedCount}, {@link #unplannedCount}) are surfaced so the consumer can
 * render either side, plus {@link #plannedVsUnplannedRatio} =
 * {@link #unplannedCount} / {@link #plannedCount} (direction: unplanned per planned;
 * higher = more reactive). It is {@code 0.0} when there are no unplanned
 * commitments; when {@link #plannedCount} is zero it is {@code 0.0} as well (the
 * ratio is undefined with no planned base — counts remain available for the
 * consumer to interpret).
 */
public record PlanMetricsDto(
    UUID planId,
    long plannedCount,
    long unplannedCount,
    long doneCount,
    Double reconciliationAccuracy,
    double plannedVsUnplannedRatio) {}
