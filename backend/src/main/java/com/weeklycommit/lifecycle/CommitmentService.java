package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.rcdo.RcdoNodeRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commitment CRUD with the lifecycle's enforcement rules (KTD 3, 4):
 *
 * <ul>
 *   <li><b>R6 — strategy link required.</b> Every create/edit must reference an
 *       existing {@code rcdo_node}; a null or unknown {@code rcdoNodeId} is a 400.
 *       Existence is checked against {@link RcdoNodeRepository} (workstream B,
 *       consumed read-only) rather than relying on the DB FK, so the rejection is
 *       a clean 400 instead of a leaked constraint violation.
 *   <li><b>R8 — classification by plan status.</b> Creating on a {@code DRAFT}
 *       plan yields a planned commitment; on a {@code LOCKED} plan it is accepted
 *       as unplanned. Creating on a {@code RECONCILING}/{@code RECONCILED} plan is
 *       rejected (409) — the plan is past the appendable window.
 *   <li><b>R7 — post-lock immutability.</b> A planned commitment cannot be edited
 *       or deleted once its plan leaves {@code DRAFT} (409). Unplanned commitments,
 *       and anything while the plan is still {@code DRAFT}, may be edited/deleted.
 * </ul>
 *
 * <p><b>Ownership.</b> Every operation loads the owning plan and requires it to
 * belong to the current principal ({@link PrincipalResolver#currentPrincipal()},
 * the same seam auditing uses). A mismatch is a 403 (deliberately chosen over a
 * 404: the resource exists, the caller is simply not its owner).
 */
@Service
public class CommitmentService {

    private final CommitmentRepository commitmentRepository;
    private final RcdoNodeRepository rcdoNodeRepository;
    private final OwnedPlanLoader ownedPlanLoader;

    public CommitmentService(
            CommitmentRepository commitmentRepository,
            RcdoNodeRepository rcdoNodeRepository,
            OwnedPlanLoader ownedPlanLoader) {
        this.commitmentRepository = commitmentRepository;
        this.rcdoNodeRepository = rcdoNodeRepository;
        this.ownedPlanLoader = ownedPlanLoader;
    }

    /**
     * Adds a commitment to {@code planId}. {@code planned} is derived from the
     * plan's status (R8): {@code DRAFT} → planned, {@code LOCKED} → unplanned;
     * later states reject the add. Requires a valid RCDO link (R6) and plan
     * ownership.
     */
    @Transactional
    public CommitmentDto create(UUID planId, UUID rcdoNodeId, String title) {
        WeeklyPlan plan = ownedPlanLoader.loadOwned(planId);

        boolean planned;
        switch (plan.getStatus()) {
            case DRAFT -> planned = true;
            case LOCKED -> planned = false;
            default ->
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Commitments cannot be added once the plan is " + plan.getStatus());
        }

        requireValidLink(rcdoNodeId);

        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(rcdoNodeId);
        c.setTitle(title);
        c.setPlanned(planned);
        return CommitmentDto.from(commitmentRepository.save(c));
    }

    /**
     * Edits a commitment's link and title. Rejected (409) if the commitment is
     * planned and its plan is past {@code DRAFT} (R7). Requires a valid RCDO link
     * (R6) and plan ownership.
     */
    @Transactional
    public CommitmentDto edit(UUID commitmentId, UUID rcdoNodeId, String title) {
        Commitment c = ownedPlanLoader.loadCommitment(commitmentId);
        requireMutable(c, ownedPlanLoader.loadOwned(c.getWeeklyPlanId()));
        requireValidLink(rcdoNodeId);

        c.setRcdoNodeId(rcdoNodeId);
        c.setTitle(title);
        return CommitmentDto.from(commitmentRepository.save(c));
    }

    /**
     * Deletes a commitment. Rejected (409) if it is planned and its plan is past
     * {@code DRAFT} (R7). Requires plan ownership.
     */
    @Transactional
    public void delete(UUID commitmentId) {
        Commitment c = ownedPlanLoader.loadCommitment(commitmentId);
        requireMutable(c, ownedPlanLoader.loadOwned(c.getWeeklyPlanId()));
        commitmentRepository.delete(c);
    }

    /** R6: the link must be present and resolve to an existing RCDO node. */
    private void requireValidLink(UUID rcdoNodeId) {
        if (rcdoNodeId == null || !rcdoNodeRepository.existsById(rcdoNodeId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A commitment must link to an existing Supporting Outcome");
        }
    }

    /** R7: a planned commitment is immutable once its plan leaves DRAFT. */
    private void requireMutable(Commitment c, WeeklyPlan plan) {
        if (c.isPlanned() && plan.getStatus() != PlanStatus.DRAFT) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Planned commitments are immutable once the plan is locked");
        }
    }
}
