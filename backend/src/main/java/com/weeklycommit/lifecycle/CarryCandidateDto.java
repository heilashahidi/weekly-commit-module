package com.weeklycommit.lifecycle;

import java.util.UUID;

/**
 * API response shape for a carry-forward candidate (U8, R12–R15): a planned
 * commitment on a {@code RECONCILED} plan whose outcome was {@code PARTIAL} or
 * {@code NOT_DONE} and is therefore offerable to roll into next week's
 * {@code DRAFT}. {@code DROPPED}/{@code DONE}/{@code UNRECONCILED} commitments are
 * never candidates (R15) so this shape only ever describes carryable work.
 *
 * <p>A record (not the entity) so the UI/agent that chooses what to carry gets a
 * clean, stable shape — mirroring {@code CommitmentDto}. {@code carryWeekCount} is
 * the source commitment's <em>current</em> count; the carried copy's count is one
 * higher (R14), set at carry time.
 */
public record CarryCandidateDto(
    UUID id,
    String title,
    UUID rcdoNodeId,
    ReconciliationStatus reconciliationStatus,
    int carryWeekCount) {

    static CarryCandidateDto from(Commitment c) {
        return new CarryCandidateDto(
            c.getId(),
            c.getTitle(),
            c.getRcdoNodeId(),
            c.getReconciliationStatus(),
            c.getCarryWeekCount());
    }
}
