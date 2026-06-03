package com.weeklycommit.lifecycle;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ManagerReview}. {@link #findByWeeklyPlanId}
 * fetches the single review for a plan (one review per plan, enforced by the unique
 * constraint), backing both the upsert lookup and the read endpoint (U9).
 */
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {

    Optional<ManagerReview> findByWeeklyPlanId(UUID weeklyPlanId);
}
