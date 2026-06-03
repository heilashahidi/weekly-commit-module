package com.weeklycommit.lifecycle;

import com.weeklycommit.config.PrincipalResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The weekly lifecycle state machine: manual forward transitions plus the
 * get-or-create of the principal's current-week plan (U4). Owns the single source
 * of transition legality — the {@link #LEGAL_TRANSITIONS} table — so both the
 * manual paths here and the deadline backstop (U7) and reconciliation submit (U5)
 * route their state changes through one validated primitive ({@link #transition}).
 *
 * <p><b>Transition table.</b> Only the three forward edges are legal:
 * {@code DRAFT -> LOCKED -> RECONCILING -> RECONCILED}. Any skip-ahead
 * ({@code DRAFT -> RECONCILED}) or backward ({@code LOCKED -> DRAFT}) move is a 409.
 *
 * <p><b>Provenance (KTD 2, R4/R5).</b> {@link #lock(UUID)} records
 * {@code lock_type = USER_LOCKED} and, when the plan has zero commitments, sets
 * {@code no_plan = true} (R5). The backstop's auto-paths set {@code AUTO_LOCKED} /
 * {@code UNRECONCILED} instead — they reuse {@link #transition} but stamp their own
 * provenance.
 *
 * <p><b>Deadlines.</b> Every transition resets {@link WeeklyPlan#setStatusDeadline}
 * to the next state's absolute instant, computed from the injected {@link Clock}
 * plus fixed offsets (see the {@code *_OFFSET} constants). RECONCILED is terminal,
 * so its deadline is cleared (null). Keeping deadlines {@code Clock}-derived makes
 * the backstop deterministically testable; the exact offset *values* are
 * configuration deferred per Scope Boundaries — these constants are sane defaults.
 *
 * <p><b>U4/U5 seam.</b> U4 owns the raw {@code RECONCILING -> RECONCILED}
 * transition legality and provenance via {@link #submitReconciled(UUID)}. The
 * "all commitments statused" GATE (R10) belongs to U5's {@code ReconciliationService},
 * which validates the precondition and then delegates to {@link #submitReconciled}
 * (or the package-visible {@link #transition} primitive) for the actual state
 * change. U4 does not duplicate the gate.
 *
 * <p><b>Ownership.</b> Transitions act on the principal's own plan: either the
 * current-week plan from {@link #getOrCreateCurrentPlan()} or a {@code planId} that
 * must be owned by the current principal (403 on mismatch), matching U3.
 */
@Service
public class LifecycleService {

    /** Days after week start before an unlocked DRAFT is overdue (config default). */
    static final Duration LOCK_OFFSET = Duration.ofDays(2);

    /** Days after lock before reconciliation must start (config default). */
    static final Duration RECONCILE_START_OFFSET = Duration.ofDays(5);

    /** Days after reconciliation start before the week auto-closes (config default). */
    static final Duration RECONCILE_CLOSE_OFFSET = Duration.ofDays(2);

    /** The only legal forward edges; everything else is a 409. */
    private static final Map<PlanStatus, PlanStatus> LEGAL_TRANSITIONS =
        Map.of(
            PlanStatus.DRAFT, PlanStatus.LOCKED,
            PlanStatus.LOCKED, PlanStatus.RECONCILING,
            PlanStatus.RECONCILING, PlanStatus.RECONCILED);

    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final PrincipalResolver principalResolver;
    private final Clock clock;

    public LifecycleService(
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            PrincipalResolver principalResolver,
            Clock clock) {
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.principalResolver = principalResolver;
        this.clock = clock;
    }

    /**
     * Returns the current principal's plan for the current ISO week, creating a
     * fresh {@code DRAFT} (with its lock deadline set) if none exists. Idempotent:
     * a second call in the same week returns the same plan without a new save.
     */
    @Transactional
    public WeeklyPlanDto getOrCreateCurrentPlan() {
        String owner = principalResolver.currentPrincipal();
        String weekKey = WeekKey.current(clock);
        WeeklyPlan plan =
            planRepository
                .findByOwnerAndWeekKey(owner, weekKey)
                .orElseGet(() -> createDraft(owner, weekKey));
        return toDto(plan);
    }

    /**
     * Manual {@code DRAFT -> LOCKED} (R3/R4): stamps {@code USER_LOCKED} and, if the
     * plan has no commitments, {@code no_plan = true} (R5). Acts on the principal's
     * current-week plan.
     */
    @Transactional
    public WeeklyPlanDto lock(UUID planId) {
        WeeklyPlan plan = loadOwnedPlan(planId);
        transition(plan, PlanStatus.LOCKED);
        plan.setLockType(LockType.USER_LOCKED);
        if (commitmentRepository.findByWeeklyPlanId(plan.getId()).isEmpty()) {
            plan.setNoPlan(true);
        }
        return toDto(planRepository.save(plan));
    }

    /** Manual {@code LOCKED -> RECONCILING}. */
    @Transactional
    public WeeklyPlanDto startReconciling(UUID planId) {
        WeeklyPlan plan = loadOwnedPlan(planId);
        transition(plan, PlanStatus.RECONCILING);
        return toDto(planRepository.save(plan));
    }

    /**
     * Raw {@code RECONCILING -> RECONCILED} transition + provenance only. The
     * "all commitments statused" gate (R10) is U5's {@code ReconciliationService},
     * which wraps this — see the class-level U4/U5 seam note.
     */
    @Transactional
    public WeeklyPlanDto submitReconciled(UUID planId) {
        WeeklyPlan plan = loadOwnedPlan(planId);
        transition(plan, PlanStatus.RECONCILED);
        return toDto(planRepository.save(plan));
    }

    /**
     * The validated state-change primitive shared by every path (manual here,
     * auto-backstop in U7, reconcile-submit in U5). Rejects any move that is not
     * the single legal forward edge from the plan's current status (409) and resets
     * the status deadline to the new state's absolute instant. Callers stamp their
     * own provenance (lock type, no_plan) after calling. Does not persist — the
     * caller saves.
     */
    void transition(WeeklyPlan plan, PlanStatus target) {
        PlanStatus from = plan.getStatus();
        if (LEGAL_TRANSITIONS.get(from) != target) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Illegal transition " + from + " -> " + target);
        }
        plan.setStatus(target);
        plan.setStatusDeadline(deadlineFor(target));
    }

    private WeeklyPlan createDraft(String owner, String weekKey) {
        WeeklyPlan plan = new WeeklyPlan();
        plan.setOwner(owner);
        plan.setWeekKey(weekKey);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setStatusDeadline(deadlineFor(PlanStatus.DRAFT));
        return planRepository.save(plan);
    }

    /**
     * The absolute instant by which a plan in {@code state} must be advanced, from
     * "now" plus the state's offset. RECONCILED is terminal — no deadline (null).
     */
    private Instant deadlineFor(PlanStatus state) {
        Instant now = Instant.now(clock);
        return switch (state) {
            case DRAFT -> now.plus(LOCK_OFFSET);
            case LOCKED -> now.plus(RECONCILE_START_OFFSET);
            case RECONCILING -> now.plus(RECONCILE_CLOSE_OFFSET);
            case RECONCILED -> null;
        };
    }

    /** Loads a plan, 404 if missing, 403 if not owned by the current principal. */
    private WeeklyPlan loadOwnedPlan(UUID planId) {
        WeeklyPlan plan =
            planRepository
                .findById(planId)
                .orElseThrow(
                    () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Weekly plan not found"));
        if (!plan.getOwner().equals(principalResolver.currentPrincipal())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Plan belongs to another principal");
        }
        return plan;
    }

    private WeeklyPlanDto toDto(WeeklyPlan plan) {
        long count = commitmentRepository.findByWeeklyPlanId(plan.getId()).size();
        return WeeklyPlanDto.from(plan, count);
    }
}
