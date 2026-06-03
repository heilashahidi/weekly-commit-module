package com.weeklycommit.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link WeeklyPlan}. {@link #findByOwnerAndWeekKey}
 * is the single-lookup "this IC's plan for this week" the (owner, week_key)
 * identity enables (KTD 7); {@link #findByStatusAndStatusDeadlineBefore} backs
 * the deadline backstop sweep (U7), which fetches plans in a given state whose
 * deadline has passed.
 */
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

    Optional<WeeklyPlan> findByOwnerAndWeekKey(String owner, String weekKey);

    List<WeeklyPlan> findByStatusAndStatusDeadlineBefore(PlanStatus status, Instant deadline);
}
