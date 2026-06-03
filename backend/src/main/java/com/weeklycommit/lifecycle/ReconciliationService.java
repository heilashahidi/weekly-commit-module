package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The reconciliation pass (U5, R9/R10/R19): in {@code RECONCILING}, ICs stamp each
 * commitment with an outcome, and the plan advances to {@code RECONCILED} only once
 * every commitment — planned and unplanned — carries an IC-set status (R10).
 *
 * <ul>
 *   <li><b>{@link #setStatus} (R9).</b> Allowed only while the owning plan is
 *       {@code RECONCILING} (else 409). The status must be one of the IC-settable
 *       values {@code DONE/PARTIAL/NOT_DONE/DROPPED}; {@code UNRECONCILED} is
 *       system-only (KTD 5, R19) and rejected here with 422 — only the deadline
 *       backstop (U7) ever sets it. The note is optional.
 *   <li><b>{@link #submit} (R10) — the GATED submit.</b> The plan must be
 *       {@code RECONCILING}; if any commitment is still unstatused the submit is
 *       rejected (422) and the plan stays {@code RECONCILING} (AE5). When all are
 *       statused, the raw {@code RECONCILING -> RECONCILED} transition runs via the
 *       shared {@link LifecycleService#transition} primitive.
 * </ul>
 *
 * <p><b>U4/U5 seam resolution.</b> There is exactly one public submit-reconciled
 * endpoint and it enforces the gate: U5's {@link ReconciliationController} owns it,
 * backed by {@link #submit}. No ungated submit exists; this service composes directly
 * over the package-visible {@link LifecycleService#transition} primitive so the
 * transition table is not duplicated.
 *
 * <p><b>Ownership.</b> Both operations require the owning plan to belong to the
 * current principal ({@link PrincipalResolver#currentPrincipal()}); a mismatch is a
 * 403, matching {@link CommitmentService}.
 */
@Service
public class ReconciliationService {

    private final CommitmentRepository commitmentRepository;
    private final WeeklyPlanRepository planRepository;
    private final OwnedPlanLoader ownedPlanLoader;
    private final LifecycleService lifecycleService;

    public ReconciliationService(
            CommitmentRepository commitmentRepository,
            WeeklyPlanRepository planRepository,
            OwnedPlanLoader ownedPlanLoader,
            LifecycleService lifecycleService) {
        this.commitmentRepository = commitmentRepository;
        this.planRepository = planRepository;
        this.ownedPlanLoader = ownedPlanLoader;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Sets a commitment's reconciliation status (+ optional note). Allowed only
     * while the owning plan is {@code RECONCILING} (R9); rejects the system-only
     * {@code UNRECONCILED} value (KTD 5, R19).
     */
    @Transactional
    public CommitmentDto setStatus(UUID commitmentId, ReconciliationStatus status, String note) {
        if (status == null || status == ReconciliationStatus.UNRECONCILED) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Reconciliation status must be one of DONE, PARTIAL, NOT_DONE, DROPPED");
        }
        Commitment c = ownedPlanLoader.loadCommitment(commitmentId);
        WeeklyPlan plan = ownedPlanLoader.loadOwned(c.getWeeklyPlanId());
        ownedPlanLoader.requireStatus(plan, PlanStatus.RECONCILING);

        c.setReconciliationStatus(status);
        c.setReconciliationNote(note);
        return CommitmentDto.from(commitmentRepository.save(c));
    }

    /**
     * The gated {@code RECONCILING -> RECONCILED} submit (R10): the plan must be
     * {@code RECONCILING} and every commitment (planned + unplanned) must carry an
     * IC-set status. If any is unstatused, the submit is rejected (422) and the
     * plan is left untouched (AE5). On success the transition runs via the shared
     * primitive.
     */
    @Transactional
    public WeeklyPlanDto submit(UUID planId) {
        WeeklyPlan plan = ownedPlanLoader.loadOwned(planId);
        ownedPlanLoader.requireStatus(plan, PlanStatus.RECONCILING);

        List<Commitment> commitments = commitmentRepository.findByWeeklyPlanId(planId);
        boolean anyUnstatused =
            commitments.stream().anyMatch(c -> c.getReconciliationStatus() == null);
        if (anyUnstatused) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Every commitment must be reconciled before the plan can be submitted");
        }

        lifecycleService.transition(plan, PlanStatus.RECONCILED);
        WeeklyPlan saved = planRepository.save(plan);
        return WeeklyPlanDto.from(saved, commitments.size());
    }

}
