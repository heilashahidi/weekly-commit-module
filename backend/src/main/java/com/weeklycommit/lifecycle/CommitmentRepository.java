package com.weeklycommit.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Commitment}. {@link #findByWeeklyPlanId}
 * fetches all commitments on a plan; {@link #findByWeeklyPlanIdAndPlanned}
 * separates planned (immutability + accuracy metric) from unplanned (reactive
 * ratio metric) commitments (R7, R8, R11). {@link #countByWeeklyPlanId} and
 * {@link #existsByWeeklyPlanId} are scalar counterparts for the latency path —
 * DTO commitment counts and empty-plan checks that would otherwise fetch every
 * row only to call {@code .size()} or {@code .isEmpty()}.
 */
public interface CommitmentRepository extends JpaRepository<Commitment, UUID> {

    List<Commitment> findByWeeklyPlanId(UUID weeklyPlanId);

    List<Commitment> findByWeeklyPlanIdAndPlanned(UUID weeklyPlanId, boolean planned);

    long countByWeeklyPlanId(UUID weeklyPlanId);

    boolean existsByWeeklyPlanId(UUID weeklyPlanId);
}
