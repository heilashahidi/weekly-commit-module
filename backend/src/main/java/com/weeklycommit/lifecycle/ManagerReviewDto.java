package com.weeklycommit.lifecycle;

import java.time.Instant;
import java.util.UUID;

/**
 * API response shape for a {@link ManagerReview}. A record (not the entity) so
 * responses don't leak persistence concerns and the manager dashboard (workstream F)
 * gets a clean, stable shape — mirroring {@code CommitmentDto}/{@code WeeklyPlanDto}.
 *
 * <p>{@code reviewer} is the reviewing principal and {@code comment} the review text.
 * {@code reviewedAt} surfaces the review timestamp (R16): it is the entity's
 * {@code lastModifiedDate} (when present, the most recent edit) falling back to
 * {@code createdDate}, so a freshly created or just-updated review both report a
 * meaningful "when".
 */
public record ManagerReviewDto(
    UUID id,
    UUID weeklyPlanId,
    String reviewer,
    String comment,
    Instant reviewedAt) {

    static ManagerReviewDto from(ManagerReview r) {
        Instant reviewedAt =
            r.getLastModifiedDate() != null ? r.getLastModifiedDate() : r.getCreatedDate();
        return new ManagerReviewDto(
            r.getId(),
            r.getWeeklyPlanId(),
            r.getReviewer(),
            r.getComment(),
            reviewedAt);
    }
}
