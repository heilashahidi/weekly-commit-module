package com.weeklycommit.lifecycle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link ManagerReview}. {@link #findByWeeklyPlanId}
 * fetches the single review for a plan (one review per plan, enforced by the unique
 * constraint), backing both the upsert lookup and the read endpoint (U9).
 */
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {

    Optional<ManagerReview> findByWeeklyPlanId(UUID weeklyPlanId);

    /**
     * The plan ids (of the given set) that have a review — a projection for the team
     * roll-up's review-done flags (F-U3), avoiding loading full review rows for the
     * board.
     */
    @Query("select r.weeklyPlanId from ManagerReview r where r.weeklyPlanId in :planIds")
    List<UUID> findReviewedPlanIds(Collection<UUID> planIds);
}
