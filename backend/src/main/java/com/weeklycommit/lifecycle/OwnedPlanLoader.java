package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralizes the 404/403/409 plan-access policy that was duplicated across the
 * lifecycle services (Lifecycle, Commitment, Reconciliation, Metrics, CarryForward).
 *
 * <p>Before this component, five services each held a byte-identical private
 * {@code loadOwnedPlan} (load + 404 + ownership 403), two repeated {@code loadCommitment}
 * (load + 404), and two carried near-identical inline status guards (409). Collapsing
 * them here makes the policy single-sourced: a change to the ownership rule or a message
 * happens in one place.
 *
 * <ul>
 *   <li><b>{@link #loadOwned(UUID)}</b> — 404 "Weekly plan not found" if missing,
 *       403 "Plan belongs to another principal" if owned by someone other than the
 *       current principal. A 403 (not 404) is deliberate: the resource exists, the
 *       caller is simply not its owner.
 *   <li><b>{@link #loadCommitment(UUID)}</b> — 404 "Commitment not found" if missing.
 *   <li><b>{@link #requireStatus(WeeklyPlan, PlanStatus)}</b> — 409 if the plan is not
 *       in the required status, used by the reconciliation and carry-forward guards.
 * </ul>
 *
 * <p>This does <em>not</em> cover {@code ManagerReviewService}, which uses an
 * existence-only check ({@code existsById}, no ownership) because any principal may
 * review any plan.
 */
@Component
public class OwnedPlanLoader {

    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final PrincipalResolver principalResolver;

    public OwnedPlanLoader(
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            PrincipalResolver principalResolver) {
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.principalResolver = principalResolver;
    }

    /** Loads a plan, 404 if missing, 403 if not owned by the current principal. */
    public WeeklyPlan loadOwned(UUID planId) {
        WeeklyPlan plan = planRepository.findById(planId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly plan not found"));
        if (!plan.getOwner().equals(principalResolver.currentPrincipal())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Plan belongs to another principal");
        }
        return plan;
    }

    /** Loads the commitment, 404 if missing. */
    public Commitment loadCommitment(UUID commitmentId) {
        return commitmentRepository.findById(commitmentId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Commitment not found"));
    }

    /**
     * Requires the plan to be in {@code required}, else 409 with the offending status
     * surfaced in the message (matching the wording the inline reconciliation and
     * carry-forward guards used).
     */
    public void requireStatus(WeeklyPlan plan, PlanStatus required) {
        if (plan.getStatus() != required) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                statusMessage(required) + " (was " + plan.getStatus() + ")");
        }
    }

    private String statusMessage(PlanStatus required) {
        return switch (required) {
            case RECONCILING ->
                "Reconciliation is only allowed while the plan is RECONCILING";
            case RECONCILED -> "Carry-forward is only available once the plan is RECONCILED";
            default -> "Plan must be " + required;
        };
    }
}
