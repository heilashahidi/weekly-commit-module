package com.weeklycommit.lifecycle;

import java.time.Instant;
import java.util.UUID;

/**
 * API response shape for a {@link WeeklyPlan}. A record (not the entity) so
 * responses don't leak persistence concerns and the frontend (workstream D) gets a
 * clean, stable shape — mirroring {@code CommitmentDto}/{@code RcdoNodeDto}.
 *
 * <p>Surfaces the lifecycle state ({@code status}) plus the two provenance signals
 * managers and metrics read directly (KTD 2): {@code lockType} (R4) and
 * {@code noPlan} (R5). {@code statusDeadline} tells the UI when the current state
 * will be auto-advanced by the backstop. {@code commitmentCount} is included so the
 * IC screen can show plan size without a second round-trip.
 */
public record WeeklyPlanDto(
    UUID id,
    String owner,
    String weekKey,
    PlanStatus status,
    LockType lockType,
    boolean noPlan,
    Instant statusDeadline,
    long commitmentCount) {

    static WeeklyPlanDto from(WeeklyPlan p, long commitmentCount) {
        return new WeeklyPlanDto(
            p.getId(),
            p.getOwner(),
            p.getWeekKey(),
            p.getStatus(),
            p.getLockType(),
            p.isNoPlan(),
            p.getStatusDeadline(),
            commitmentCount);
    }
}
