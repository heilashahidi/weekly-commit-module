package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
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
 * <p><b>Authorization (intentional deferral).</b> Any authenticated principal may
 * review any plan. There is no "is this principal actually this IC's manager" check
 * and no reviewer-vs-owner restriction: that requires an org/reporting model that
 * does not exist yet (workstream F territory — see the plan's Deferred Implementation
 * Notes). The reviewer column is simply stamped with the current principal.
 */
@Service
public class ManagerReviewService {

    private final ManagerReviewRepository reviewRepository;
    private final WeeklyPlanRepository planRepository;
    private final PrincipalResolver principalResolver;

    public ManagerReviewService(
            ManagerReviewRepository reviewRepository,
            WeeklyPlanRepository planRepository,
            PrincipalResolver principalResolver) {
        this.reviewRepository = reviewRepository;
        this.planRepository = planRepository;
        this.principalResolver = principalResolver;
    }

    /**
     * Creates or updates the review for {@code planId}. The plan must exist (404
     * otherwise). The reviewer is stamped with the current principal; on an existing
     * review the comment is updated and the reviewer refreshed to the latest reviewer.
     * Never touches the plan or its commitments.
     */
    @Transactional
    public ManagerReviewDto upsertReview(UUID planId, String comment) {
        requirePlanExists(planId);
        String reviewer = principalResolver.currentPrincipal();

        ManagerReview review =
            reviewRepository
                .findByWeeklyPlanId(planId)
                .orElseGet(
                    () -> {
                        ManagerReview r = new ManagerReview();
                        r.setWeeklyPlanId(planId);
                        return r;
                    });
        review.setReviewer(reviewer);
        review.setComment(comment);
        return ManagerReviewDto.from(reviewRepository.save(review));
    }

    /**
     * Returns the review for {@code planId}, or empty if none exists. The plan must
     * exist (404 otherwise) so a read against a bogus plan id is distinguishable from
     * a real plan with no review yet.
     */
    @Transactional(readOnly = true)
    public Optional<ManagerReviewDto> getReview(UUID planId) {
        requirePlanExists(planId);
        return reviewRepository.findByWeeklyPlanId(planId).map(ManagerReviewDto::from);
    }

    /** The reviewed plan must exist; 404 otherwise. */
    private void requirePlanExists(UUID planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly plan not found");
        }
    }
}
