package com.weeklycommit.lifecycle;

import java.util.UUID;

/**
 * API response shape for a {@link Commitment}. A record (not the entity) so
 * responses don't leak persistence concerns (audit fields, JPA internals) and the
 * frontend (workstream D) gets a clean, stable shape — mirroring {@code RcdoNodeDto}.
 *
 * <p>{@code planned} tells the UI whether the commitment was planned in {@code DRAFT}
 * (immutable once locked, R7) or appended as unplanned after lock (R8).
 * Reconciliation and carry-forward fields are surfaced read-only here; they are
 * mutated by U5/U8, not this unit.
 */
public record CommitmentDto(
    UUID id,
    UUID weeklyPlanId,
    UUID rcdoNodeId,
    String title,
    boolean planned,
    ReconciliationStatus reconciliationStatus,
    String reconciliationNote,
    UUID carriedFromId,
    int carryWeekCount) {

    static CommitmentDto from(Commitment c) {
        return new CommitmentDto(
            c.getId(),
            c.getWeeklyPlanId(),
            c.getRcdoNodeId(),
            c.getTitle(),
            c.isPlanned(),
            c.getReconciliationStatus(),
            c.getReconciliationNote(),
            c.getCarriedFromId(),
            c.getCarryWeekCount());
    }
}
