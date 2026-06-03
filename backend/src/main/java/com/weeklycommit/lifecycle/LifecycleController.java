package com.weeklycommit.lifecycle;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the weekly plan lifecycle. JWT-secured by default (only
 * {@code /health} is public per SecurityConfig). Thin — all transition legality,
 * provenance, and ownership live in {@link LifecycleService}; this layer maps HTTP
 * to service calls and the response record.
 *
 * <p>Routing: the principal's current-week plan is fetched/created at
 * {@code GET /api/lifecycle/plans/current}; each manual forward transition is a
 * {@code POST} of the plan's id under {@code .../transitions/...}.
 */
@RestController
public class LifecycleController {

    private final LifecycleService service;

    public LifecycleController(LifecycleService service) {
        this.service = service;
    }

    /** Get-or-create the current principal's plan for the current ISO week. */
    @GetMapping("/api/lifecycle/plans/current")
    public WeeklyPlanDto currentPlan() {
        return service.getOrCreateCurrentPlan();
    }

    /** Manual {@code DRAFT -> LOCKED} (USER_LOCKED). */
    @PostMapping("/api/lifecycle/plans/{planId}/transitions/lock")
    public WeeklyPlanDto lock(@PathVariable UUID planId) {
        return service.lock(planId);
    }

    /** Manual {@code LOCKED -> RECONCILING}. */
    @PostMapping("/api/lifecycle/plans/{planId}/transitions/start-reconciling")
    public WeeklyPlanDto startReconciling(@PathVariable UUID planId) {
        return service.startReconciling(planId);
    }

    /**
     * Manual {@code RECONCILING -> RECONCILED}. The all-statused gate (R10) is
     * added by U5's reconciliation controller; this exposes the raw transition.
     */
    @PostMapping("/api/lifecycle/plans/{planId}/transitions/submit-reconciled")
    public WeeklyPlanDto submitReconciled(@PathVariable UUID planId) {
        return service.submitReconciled(planId);
    }
}
