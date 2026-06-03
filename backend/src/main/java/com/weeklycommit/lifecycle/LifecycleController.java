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
    private final MetricsService metricsService;

    public LifecycleController(LifecycleService service, MetricsService metricsService) {
        this.service = service;
        this.metricsService = metricsService;
    }

    /** Get-or-create the current principal's plan for the current ISO week. */
    @GetMapping("/api/lifecycle/plans/current")
    public WeeklyPlanDto currentPlan() {
        return service.getOrCreateCurrentPlan();
    }

    /**
     * The two-axis reconciliation metrics for a plan (U6, R11): reconciliation
     * accuracy (planned only) and the planned-vs-unplanned ratio. Ownership-checked
     * (404 missing, 403 mismatch) in {@link MetricsService}.
     */
    @GetMapping("/api/lifecycle/plans/{planId}/metrics")
    public PlanMetricsDto metrics(@PathVariable UUID planId) {
        return metricsService.getMetrics(planId);
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

    // NOTE (U4/U5 seam): the RECONCILING -> RECONCILED submit endpoint lives in
    // ReconciliationController (U5) and is the GATED one (all commitments statused,
    // R10). The raw, ungated submit is intentionally NOT exposed here, so there is
    // exactly one public path to RECONCILED and it always enforces the gate.
    // LifecycleService.submitReconciled remains as the package-internal raw
    // transition primitive that ReconciliationService composes over.
}
