package com.weeklycommit.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Commitment}. {@link #findByWeeklyPlanId}
 * fetches all commitments on a plan; {@link #findByWeeklyPlanIdAndPlanned}
 * separates planned (immutability + accuracy metric) from unplanned (reactive
 * ratio metric) commitments (R7, R8, R11).
 */
public interface CommitmentRepository extends JpaRepository<Commitment, UUID> {

    List<Commitment> findByWeeklyPlanId(UUID weeklyPlanId);

    List<Commitment> findByWeeklyPlanIdAndPlanned(UUID weeklyPlanId, boolean planned);
}
