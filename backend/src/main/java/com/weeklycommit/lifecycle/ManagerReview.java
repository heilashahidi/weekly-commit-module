package com.weeklycommit.lifecycle;

import com.weeklycommit.common.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A non-blocking manager-review annotation attached to a {@link WeeklyPlan} (R16,
 * R17). It lives in its <b>own</b> table referencing {@code weekly_plan_id} — it
 * never touches the immutable plan or its commitments (resolves origin Outstanding
 * Question (c)). Crucially, the lifecycle state machine ({@link LifecycleService})
 * does not consult reviews: a review can be present or absent at any state and a
 * transition never depends on it (R17, proven by the AE8 test).
 *
 * <p><b>One review per plan.</b> A unique constraint on {@code weekly_plan_id}
 * models a single review per plan; {@link ManagerReviewService} upserts (create if
 * absent, otherwise update the comment). This matches the plan's "create/update a
 * review for a plan" framing.
 *
 * <p><b>Reviewer identity.</b> {@link #reviewer} is stored explicitly (set to the
 * current principal at upsert) so it is queryable without relying on audit
 * semantics, even though {@code created_by}/{@code last_modified_by} from
 * {@link AbstractAuditingEntity} also capture the principal. {@link #comment} is the
 * review text (nullable).
 *
 * <p>Extends {@link AbstractAuditingEntity} for created/modified auditing — the
 * review timestamp (R16) is its {@code created_date}/{@code last_modified_date}. The
 * table is owned by Flyway ({@code V5__manager_review.sql}); this mapping must match
 * that DDL exactly because Hibernate runs in {@code validate}.
 */
@Entity
@Table(
    name = "manager_review",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_manager_review_weekly_plan_id",
            columnNames = {"weekly_plan_id"}))
@Getter
@Setter
public class ManagerReview extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Reviewed plan (DB FK to weekly_plan); plain UUID per the repo convention. */
    @Column(name = "weekly_plan_id", nullable = false)
    private UUID weeklyPlanId;

    /** The reviewing principal, stored explicitly so it is queryable (R16). */
    @Column(name = "reviewer", length = 255)
    private String reviewer;

    /** Free-text review note; nullable. */
    @Column(name = "comment")
    private String comment;
}
