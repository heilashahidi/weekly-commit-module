package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.manager.ReportingRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The non-blocking manager-review overlay (U9, R16/R17). A review is a pure
 * annotation on a {@link WeeklyPlan}: it lives in its own {@link ManagerReview}
 * table and never mutates the plan or its commitments (resolves origin Outstanding
 * Question (c)), and the lifecycle state machine ({@link LifecycleService}) never
 * consults it — transitions are review-independent (R17).
 *
 * <p><b>One review per plan (upsert).</b> {@link #upsertReview} creates a review if
 * none exists, otherwise updates the existing one's comment. The unique constraint
 * on {@code weekly_plan_id} backs the one-per-plan model.
 *
 * <p><b>Authorization (workstream F).</b> Writing a review is restricted to the
 * reviewed plan owner's direct manager (per the seeded reporting mapping) — the owner
 * may not self-review (403). Reading a review is allowed for the owner OR the owner's
 * manager. This closes the prior open write (any authenticated principal could review
 * any plan) and adds the read check, which previously did not exist at all. The
 * reviewer column is stamped with the current principal (the manager).
 */
@Service
public class ManagerReviewService {

    /** Upper bound on a review comment; a longer body is a 400 (the column is TEXT). */
    static final int MAX_COMMENT_LENGTH = 2000;

    private final ManagerReviewRepository reviewRepository;
    private final WeeklyPlanRepository planRepository;
    private final ReportingRepository reportingRepository;
    private final PrincipalResolver principalResolver;

    public ManagerReviewService(
            ManagerReviewRepository reviewRepository,
            WeeklyPlanRepository planRepository,
            ReportingRepository reportingRepository,
            PrincipalResolver principalResolver) {
        this.reviewRepository = reviewRepository;
        this.planRepository = planRepository;
        this.reportingRepository = reportingRepository;
        this.principalResolver = principalResolver;
    }

    /**
     * Creates or updates the review for {@code planId}. The plan must exist (404) and
     * the current principal must be the plan owner's direct manager (403 otherwise,
     * including the owner self-reviewing). The comment is bounded ({@link
     * #MAX_COMMENT_LENGTH}, 400 if longer). The reviewer is stamped with the current
     * principal; on an existing review the comment and reviewer are refreshed. Never
     * touches the plan or its commitments.
     */
    @Transactional
    public ManagerReviewDto upsertReview(UUID planId, String comment) {
        WeeklyPlan plan = loadPlan(planId);
        String principal = principalResolver.currentPrincipal();
        requireManagerOf(principal, plan.getOwner());
        requireCommentWithinBounds(comment);

        ManagerReview review =
            reviewRepository
                .findByWeeklyPlanId(planId)
                .orElseGet(
                    () -> {
                        ManagerReview r = new ManagerReview();
                        r.setWeeklyPlanId(planId);
                        return r;
                    });
        review.setReviewer(principal);
        review.setComment(comment);
        return ManagerReviewDto.from(reviewRepository.save(review));
    }

    /**
     * Returns the review for {@code planId}, or empty if none exists. The plan must
     * exist (404) and the current principal must be the owner OR the owner's manager
     * (403 otherwise) — so the IC still reads their own review and the manager reads
     * their reports'. Empty is a valid state distinct from a bogus plan id.
     */
    @Transactional(readOnly = true)
    public Optional<ManagerReviewDto> getReview(UUID planId) {
        WeeklyPlan plan = loadPlan(planId);
        String principal = principalResolver.currentPrincipal();
        requireOwnerOrManagerOf(principal, plan.getOwner());
        return reviewRepository.findByWeeklyPlanId(planId).map(ManagerReviewDto::from);
    }

    /** The reviewed plan; 404 if missing. */
    private WeeklyPlan loadPlan(UUID planId) {
        return planRepository
            .findById(planId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly plan not found"));
    }

    /** The principal must be {@code owner}'s direct manager; 403 otherwise (owner included). */
    private void requireManagerOf(String principal, String owner) {
        if (!reportingRepository.existsByManagerSubAndReportSub(principal, owner)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Only the plan owner's manager may review this plan");
        }
    }

    /** The principal must be the owner OR the owner's manager; 403 otherwise. */
    private void requireOwnerOrManagerOf(String principal, String owner) {
        boolean ok =
            owner.equals(principal)
                || reportingRepository.existsByManagerSubAndReportSub(principal, owner);
        if (!ok) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Plan belongs to another principal");
        }
    }

    private void requireCommentWithinBounds(String comment) {
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Review comment exceeds " + MAX_COMMENT_LENGTH + " characters");
        }
    }
}
