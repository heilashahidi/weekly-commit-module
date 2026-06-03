package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
    private final OwnedPlanLoader ownedPlanLoader;
    private final LifecycleService lifecycleService;

    public CarryForwardService(
            CommitmentRepository commitmentRepository,
            OwnedPlanLoader ownedPlanLoader,
            LifecycleService lifecycleService) {
        this.commitmentRepository = commitmentRepository;
        this.ownedPlanLoader = ownedPlanLoader;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Lists the carry candidates for a {@code RECONCILED} plan (R12/R15): planned
     * commitments marked {@code PARTIAL} or {@code NOT_DONE}. Ownership-checked.
     */
    @Transactional(readOnly = true)
    public List<CarryCandidateDto> listCandidates(UUID planId) {
        WeeklyPlan plan = ownedPlanLoader.loadOwned(planId);
        ownedPlanLoader.requireStatus(plan, PlanStatus.RECONCILED);
        return candidates(planId).stream().map(CarryCandidateDto::from).toList();
    }

    /**
     * Seeds the owner's next-week {@code DRAFT} with the IC-selected subset of carry
     * candidates (R13/R14). The source plan must be {@code RECONCILED}; every id must
     * be a valid candidate of it; the next-week plan must still be {@code DRAFT}
     * (else 409 — seeding planned commitments into a locked-or-later plan would
     * violate R8/immutability). Idempotent: a source already carried into next week
     * (its id present as a {@code carriedFromId} there) is skipped, so re-POSTing the
     * same selection does not create duplicates. Returns the newly created commitments.
     */
    @Transactional
    public List<CommitmentDto> carry(UUID planId, List<UUID> selectedCommitmentIds) {
        WeeklyPlan plan = ownedPlanLoader.loadOwned(planId);
        ownedPlanLoader.requireStatus(plan, PlanStatus.RECONCILED);

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

        // R8/immutability: seeding planned commitments is only legal into a DRAFT.
        // If next week has already been locked (or beyond), reject rather than insert.
        if (nextPlan.getStatus() != PlanStatus.DRAFT) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot carry into a plan that is no longer DRAFT (was " + nextPlan.getStatus() + ")");
        }

        // Idempotency: a source already carried into next week (its id appears as a
        // carriedFromId there) is skipped, so re-POSTing /carry is a no-op for it
        // rather than seeding a duplicate copy.
        Set<UUID> alreadyCarried =
            commitmentRepository.findByWeeklyPlanId(nextPlan.getId()).stream()
                .map(Commitment::getCarriedFromId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<CommitmentDto> carried = new ArrayList<>();
        for (Commitment source : candidates) {
            if (!selected.contains(source.getId())) {
                continue; // IC-selected only (R13): unselected candidates are not carried.
            }
            if (alreadyCarried.contains(source.getId())) {
                continue; // Already carried into next week (idempotent re-POST).
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

}
