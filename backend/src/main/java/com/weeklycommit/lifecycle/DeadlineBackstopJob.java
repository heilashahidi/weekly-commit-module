package com.weeklycommit.lifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * <p><b>Per-plan isolation.</b> The top-level {@link #sweep()} is intentionally
 * <em>not</em> transactional; it is the orchestrator. Each plan is advanced in its
 * own {@code REQUIRES_NEW} transaction on the separate {@link LifecycleAutoAdvancer}
 * bean, and each call is wrapped in try/catch. So if one plan's transition throws
 * (e.g. it was concurrently advanced and the transition is now a 409), only that
 * plan rolls back and is logged — the rest of the batch still commits, and the
 * failure does not re-fail forever on every 60s pass. A top-level catch additionally
 * guarantees an escaped error never kills the {@code @Scheduled} future.
 *
 * <p>The legal state change for each edge runs through the shared
 * {@link LifecycleService#transition} primitive (via {@link LifecycleAutoAdvancer}),
 * so the transition table is not duplicated; only the auto-path-specific provenance
 * ({@code AUTO_LOCKED}, {@code no_plan}, {@code UNRECONCILED}) is added.
 * {@code transition} also resets/clears {@code statusDeadline}, so a plan advanced on
 * a pass gets a fresh deadline (and RECONCILED gets a null one) and is therefore not
 * re-swept.
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

    private static final Logger log = LoggerFactory.getLogger(DeadlineBackstopJob.class);

    private final WeeklyPlanRepository planRepository;
    private final LifecycleAutoAdvancer autoAdvancer;
    private final Clock clock;

    public DeadlineBackstopJob(
            WeeklyPlanRepository planRepository,
            LifecycleAutoAdvancer autoAdvancer,
            Clock clock) {
        this.planRepository = planRepository;
        this.autoAdvancer = autoAdvancer;
        this.clock = clock;
    }

    /**
     * Sweeps all three forward edges for overdue plans. Scheduled in production
     * (default 60s between runs), but directly invokable by tests after advancing
     * the clock. Non-transactional orchestrator: each plan is advanced in its own
     * transaction (see class doc) and per-plan failures are isolated and logged, so
     * one bad plan never aborts the batch or kills the scheduler.
     */
    @Scheduled(fixedDelayString = "${wc.lifecycle.backstop-interval-ms:60000}")
    public void sweep() {
        try {
            Instant now = Instant.now(clock);
            int locked = advanceEach(PlanStatus.DRAFT, now, autoAdvancer::autoLock);
            int started = advanceEach(PlanStatus.LOCKED, now, autoAdvancer::autoStartReconciling);
            int closed = advanceEach(PlanStatus.RECONCILING, now, autoAdvancer::autoClose);
            if (locked + started + closed > 0) {
                log.info(
                    "Deadline backstop pass: auto-locked={}, auto-advanced-to-reconciling={}, "
                        + "auto-closed={}",
                    locked,
                    started,
                    closed);
            }
        } catch (RuntimeException e) {
            // Never let an unexpected error escape and kill the @Scheduled future.
            log.error("Deadline backstop sweep failed unexpectedly", e);
        }
    }

    /**
     * Advances every overdue plan in {@code from} via {@code advance}, each in its
     * own transaction. A per-plan failure is logged and skipped so the batch
     * continues. Returns the count successfully advanced.
     */
    private int advanceEach(PlanStatus from, Instant now, Consumer<UUID> advance) {
        List<WeeklyPlan> overdue = planRepository.findByStatusAndStatusDeadlineBefore(from, now);
        int advanced = 0;
        for (WeeklyPlan plan : overdue) {
            try {
                advance.accept(plan.getId());
                advanced++;
            } catch (RuntimeException e) {
                log.warn(
                    "Deadline backstop failed to auto-advance plan {} from {}: {}",
                    plan.getId(),
                    from,
                    e.toString());
            }
        }
        return advanced;
    }
}
