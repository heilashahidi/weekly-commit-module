package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Carry-forward as a seeding action (U8, KTD 1, R12–R15): "Carry Forward" is not a
 * fifth persisted plan status but the operation performed at/after {@code RECONCILED}
 * that flows unfinished work into the <em>next</em> week's {@code DRAFT}.
 *
 * <ul>
 *   <li><b>{@link #listCandidates} (R12, R15).</b> On a {@code RECONCILED} plan (else
 *       409), the candidates are the <em>planned</em> commitments whose outcome was
 *       {@code PARTIAL} or {@code NOT_DONE}. {@code DROPPED} is deliberately
 *       excluded (R15), as are {@code DONE}, the system-only {@code UNRECONCILED},
 *       unstatused, and unplanned commitments — only carryable work is offered.
 *   <li><b>{@link #carry} (R13, R14).</b> IC-selected only: callers pass the subset
 *       of candidate ids to roll over; unselected candidates are not carried (R13,
 *       never automatic). Each selected id must be a valid candidate of <em>this</em>
 *       plan (else 422). The selected ones are seeded into the owner's next-week
 *       {@code DRAFT} (get-or-create via {@link LifecycleService#getOrCreatePlan}):
 *       a fresh planned {@link Commitment} pre-linked to the same {@code rcdo_node_id}
 *       (R12), with {@code carriedFromId} = source id and {@code carryWeekCount} =
 *       source count + 1 (R14, two-week rollovers reach 2). The carried copy starts
 *       unreconciled ({@code reconciliationStatus = null}).
 * </ul>
 *
 * <p><b>Ownership.</b> Both operations require the plan to belong to the current
 * principal ({@link PrincipalResolver#currentPrincipal()}); a mismatch is a 403,
 * matching the other lifecycle services.
 */
@Service
public class CarryForwardService {

    /** The two outcomes that make a planned commitment a carry candidate (R12). */
    private static final Set<ReconciliationStatus> CARRYABLE =
        EnumSet.of(ReconciliationStatus.PARTIAL, ReconciliationStatus.NOT_DONE);

    private final CommitmentRepository commitmentRepository;
    private final WeeklyPlanRepository planRepository;
    private final PrincipalResolver principalResolver;
    private final LifecycleService lifecycleService;

    public CarryForwardService(
            CommitmentRepository commitmentRepository,
            WeeklyPlanRepository planRepository,
            PrincipalResolver principalResolver,
            LifecycleService lifecycleService) {
        this.commitmentRepository = commitmentRepository;
        this.planRepository = planRepository;
        this.principalResolver = principalResolver;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Lists the carry candidates for a {@code RECONCILED} plan (R12/R15): planned
     * commitments marked {@code PARTIAL} or {@code NOT_DONE}. Ownership-checked.
     */
    @Transactional(readOnly = true)
    public List<CarryCandidateDto> listCandidates(UUID planId) {
        WeeklyPlan plan = loadOwnedPlan(planId);
        requireReconciled(plan);
        return candidates(planId).stream().map(CarryCandidateDto::from).toList();
    }

    /**
     * Seeds the owner's next-week {@code DRAFT} with the IC-selected subset of carry
     * candidates (R13/R14). The source plan must be {@code RECONCILED}; every id must
     * be a valid candidate of it. Returns the newly created commitments.
     */
    @Transactional
    public List<CommitmentDto> carry(UUID planId, List<UUID> selectedCommitmentIds) {
        WeeklyPlan plan = loadOwnedPlan(planId);
        requireReconciled(plan);

        List<Commitment> candidates = candidates(planId);
        Set<UUID> candidateIds =
            candidates.stream().map(Commitment::getId).collect(Collectors.toSet());

        List<UUID> selected = selectedCommitmentIds == null ? List.of() : selectedCommitmentIds;
        for (UUID id : selected) {
            if (!candidateIds.contains(id)) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Commitment " + id + " is not a carry candidate of this plan");
            }
        }

        String nextWeekKey = WeekKey.nextWeek(plan.getWeekKey());
        WeeklyPlan nextPlan = lifecycleService.getOrCreatePlan(plan.getOwner(), nextWeekKey);

        List<CommitmentDto> carried = new ArrayList<>();
        for (Commitment source : candidates) {
            if (!selected.contains(source.getId())) {
                continue; // IC-selected only (R13): unselected candidates are not carried.
            }
            Commitment copy = new Commitment();
            copy.setWeeklyPlanId(nextPlan.getId());
            copy.setRcdoNodeId(source.getRcdoNodeId());
            copy.setTitle(source.getTitle());
            copy.setPlanned(true);
            copy.setCarriedFromId(source.getId());
            copy.setCarryWeekCount(source.getCarryWeekCount() + 1);
            carried.add(CommitmentDto.from(commitmentRepository.save(copy)));
        }
        return carried;
    }

    /** Planned commitments of {@code planId} whose outcome is PARTIAL or NOT_DONE. */
    private List<Commitment> candidates(UUID planId) {
        return commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true).stream()
            .filter(c -> CARRYABLE.contains(c.getReconciliationStatus()))
            .toList();
    }

    private void requireReconciled(WeeklyPlan plan) {
        if (plan.getStatus() != PlanStatus.RECONCILED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Carry-forward is only available once the plan is RECONCILED (was "
                    + plan.getStatus() + ")");
        }
    }

    /** Loads a plan, 404 if missing, 403 if not owned by the current principal. */
    private WeeklyPlan loadOwnedPlan(UUID planId) {
        WeeklyPlan plan = planRepository.findById(planId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly plan not found"));
        if (!plan.getOwner().equals(principalResolver.currentPrincipal())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Plan belongs to another principal");
        }
        return plan;
    }
}
