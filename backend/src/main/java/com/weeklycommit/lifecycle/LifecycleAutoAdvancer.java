package com.weeklycommit.lifecycle;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-plan auto-advance methods for the deadline backstop, each in its <b>own</b>
 * {@code REQUIRES_NEW} transaction so one plan's failure (e.g. a concurrent advance
 * that makes the transition a 409) rolls back only that plan, not the whole sweep
 * batch.
 *
 * <p>This lives on a <em>separate</em> bean from {@link DeadlineBackstopJob} on
 * purpose: Spring's transactional proxy is bypassed on self-invocation, so a
 * {@code @Transactional} method called from another method of the same bean would
 * not actually start a new transaction. {@link DeadlineBackstopJob} injects this
 * bean and calls through the proxy, so each per-plan transaction is honored.
 *
 * <p>Each method reuses the shared {@link LifecycleService#transition} primitive for
 * the legal state change and only adds the auto-path-specific provenance
 * ({@code AUTO_LOCKED}, {@code no_plan}, {@code UNRECONCILED}); the transition table
 * is not duplicated here.
 */
@Component
public class LifecycleAutoAdvancer {

    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final LifecycleService lifecycleService;

    public LifecycleAutoAdvancer(
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            LifecycleService lifecycleService) {
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.lifecycleService = lifecycleService;
    }

    /** Edge 1: overdue DRAFT -> LOCKED, stamping AUTO_LOCKED (+ no_plan if empty). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoLock(UUID planId) {
        WeeklyPlan plan = require(planId);
        lifecycleService.transition(plan, PlanStatus.LOCKED);
        plan.setLockType(LockType.AUTO_LOCKED);
        if (!commitmentRepository.existsByWeeklyPlanId(plan.getId())) {
            plan.setNoPlan(true);
        }
        planRepository.save(plan);
    }

    /** Edge 2: overdue LOCKED -> RECONCILING. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoStartReconciling(UUID planId) {
        WeeklyPlan plan = require(planId);
        lifecycleService.transition(plan, PlanStatus.RECONCILING);
        planRepository.save(plan);
    }

    /** Edge 3: overdue RECONCILING -> RECONCILED, filling UNRECONCILED first (R19). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoClose(UUID planId) {
        WeeklyPlan plan = require(planId);
        for (Commitment c : commitmentRepository.findByWeeklyPlanId(plan.getId())) {
            if (c.getReconciliationStatus() == null) {
                c.setReconciliationStatus(ReconciliationStatus.UNRECONCILED);
                commitmentRepository.save(c);
            }
        }
        lifecycleService.transition(plan, PlanStatus.RECONCILED);
        planRepository.save(plan);
    }

    /** Re-load inside the new transaction; 404 if it vanished between finder and advance. */
    private WeeklyPlan require(UUID planId) {
        return planRepository
            .findById(planId)
            .orElseThrow(() -> new IllegalStateException("Plan disappeared during sweep: " + planId));
    }
}
