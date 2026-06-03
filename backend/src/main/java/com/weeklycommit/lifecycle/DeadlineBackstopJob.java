package com.weeklycommit.lifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The deadline backstop (U7, KTD 6, R18/R19/R4/R5): a single scheduled sweep that
 * auto-advances any plan whose actor missed the deadline — on <b>every</b> forward
 * edge, not just lock — and records auto-provenance so auto-driven progress stays
 * distinguishable from IC action.
 *
 * <p>The sweep handles all three forward edges, each found via
 * {@link WeeklyPlanRepository#findByStatusAndStatusDeadlineBefore} keyed on the
 * source status and a deadline strictly before {@code Instant.now(clock)}:
 * <ol>
 *   <li><b>{@code DRAFT -> LOCKED}.</b> Overdue drafts auto-lock with
 *       {@code lock_type = AUTO_LOCKED} (the R4 distinction from U4's
 *       {@code USER_LOCKED}); an empty draft also gets {@code no_plan = true}
 *       (R5/AE2).
 *   <li><b>{@code LOCKED -> RECONCILING}.</b> Overdue locked plans advance.
 *   <li><b>{@code RECONCILING -> RECONCILED}.</b> Overdue reconciling plans
 *       auto-close: every commitment still carrying a null reconciliation status is
 *       stamped {@code UNRECONCILED} — never {@code DONE} (R19/AE9) — then the plan
 *       advances.
 * </ol>
 *
 * <p>The legal state change for each edge runs through the shared
 * {@link LifecycleService#transition} primitive, so the transition table is not
 * duplicated; this job only adds the auto-path-specific provenance
 * ({@code AUTO_LOCKED}, {@code no_plan}, {@code UNRECONCILED}). {@code transition}
 * also resets/clears {@code statusDeadline}, so a plan advanced on a pass gets a
 * fresh deadline (and RECONCILED gets a null one) and is therefore not re-swept.
 *
 * <p><b>Determinism.</b> All deadline math reads "now" from the injected
 * {@link Clock} (U0), so tests advance time and call {@link #sweep()} directly.
 *
 * <p><b>Idempotency/safety.</b> A plan not past its deadline is never returned by
 * the finder (its deadline is {@code >= now}); a terminal {@code RECONCILED} plan
 * has a null deadline, which {@code statusDeadline < now} never matches, so it is
 * never swept.
 */
@Component
public class DeadlineBackstopJob {

    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final LifecycleService lifecycleService;
    private final Clock clock;

    public DeadlineBackstopJob(
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            LifecycleService lifecycleService,
            Clock clock) {
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    /**
     * Sweeps all three forward edges for overdue plans. Scheduled in production
     * (default 60s between runs), but directly invokable by tests after advancing
     * the clock. Runs in one transaction so all auto-advances on a pass commit
     * together.
     */
    @Scheduled(fixedDelayString = "${wc.lifecycle.backstop-interval-ms:60000}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now(clock);
        autoLockOverdueDrafts(now);
        autoStartReconcilingOverdueLocked(now);
        autoCloseOverdueReconciling(now);
    }

    /** Edge 1: overdue DRAFT -> LOCKED, stamping AUTO_LOCKED (+ no_plan if empty). */
    private void autoLockOverdueDrafts(Instant now) {
        List<WeeklyPlan> overdue =
            planRepository.findByStatusAndStatusDeadlineBefore(PlanStatus.DRAFT, now);
        for (WeeklyPlan plan : overdue) {
            lifecycleService.transition(plan, PlanStatus.LOCKED);
            plan.setLockType(LockType.AUTO_LOCKED);
            if (!commitmentRepository.existsByWeeklyPlanId(plan.getId())) {
                plan.setNoPlan(true);
            }
            planRepository.save(plan);
        }
    }

    /** Edge 2: overdue LOCKED -> RECONCILING. */
    private void autoStartReconcilingOverdueLocked(Instant now) {
        List<WeeklyPlan> overdue =
            planRepository.findByStatusAndStatusDeadlineBefore(PlanStatus.LOCKED, now);
        for (WeeklyPlan plan : overdue) {
            lifecycleService.transition(plan, PlanStatus.RECONCILING);
            planRepository.save(plan);
        }
    }

    /** Edge 3: overdue RECONCILING -> RECONCILED, filling UNRECONCILED first (R19). */
    private void autoCloseOverdueReconciling(Instant now) {
        List<WeeklyPlan> overdue =
            planRepository.findByStatusAndStatusDeadlineBefore(PlanStatus.RECONCILING, now);
        for (WeeklyPlan plan : overdue) {
            for (Commitment c : commitmentRepository.findByWeeklyPlanId(plan.getId())) {
                if (c.getReconciliationStatus() == null) {
                    c.setReconciliationStatus(ReconciliationStatus.UNRECONCILED);
                    commitmentRepository.save(c);
                }
            }
            lifecycleService.transition(plan, PlanStatus.RECONCILED);
            planRepository.save(plan);
        }
    }
}
