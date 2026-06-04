package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.manager.ReportingRepository;
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
 * <p><b>Manager scope (workstream F).</b> {@link #loadOwnedOrManaged(UUID)} widens
 * read access to "owner OR the owner's manager" for the manager-facing read paths
 * (plan, commitments, metrics). The owner-only {@link #loadOwned(UUID)} still backs
 * every write/transition path, so a manager can read a report's plan but never
 * mutate it. {@code ManagerReviewService} enforces its own manager-of-owner rule for
 * the review write (the owner may not self-review).
 */
@Component
public class OwnedPlanLoader {

    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final ReportingRepository reportingRepository;
    private final PrincipalResolver principalResolver;

    public OwnedPlanLoader(
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            ReportingRepository reportingRepository,
            PrincipalResolver principalResolver) {
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.reportingRepository = reportingRepository;
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

    /**
     * Loads a plan for a manager-facing read: 404 if missing, 403 unless the current
     * principal is the owner OR the owner's direct manager (per the seeded reporting
     * mapping). Used by the read paths a manager must reach (plan, commitments,
     * metrics); write/transition paths keep {@link #loadOwned(UUID)}.
     */
    public WeeklyPlan loadOwnedOrManaged(UUID planId) {
        WeeklyPlan plan = planRepository.findById(planId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Weekly plan not found"));
        String principal = principalResolver.currentPrincipal();
        boolean owner = plan.getOwner().equals(principal);
        boolean manager =
            reportingRepository.existsByManagerSubAndReportSub(principal, plan.getOwner());
        if (!owner && !manager) {
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
