package com.weeklycommit.lifecycle;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the reconciliation pass (U5). JWT-secured by default (only
 * {@code /health} is public per SecurityConfig). Thin — all rules (RECONCILING-only
 * windows, the all-statused submit gate, the system-only {@code UNRECONCILED}
 * rejection, ownership) live in {@link ReconciliationService}; this layer only maps
 * HTTP to service calls and the response record.
 *
 * <p><b>Single gated submit (U4/U5 seam).</b> This controller owns the one public
 * {@code submit-reconciled} endpoint, and it is the GATED one (R10): the raw,
 * ungated transition that U4 once exposed has been removed from
 * {@code LifecycleController}, so there is exactly one HTTP path to
 * {@code RECONCILED} and it always enforces the all-statused precondition. The path
 * is kept under the {@code /api/lifecycle} namespace for consistency with U4.
 */
@RestController
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    /** Request body for setting a commitment's reconciliation status + optional note. */
    public record StatusRequest(ReconciliationStatus status, String note) {}

    /** Set a single commitment's reconciliation status (R9); RECONCILING-only. */
    @PutMapping("/api/lifecycle/commitments/{id}/status")
    public CommitmentDto setStatus(
            @PathVariable UUID id, @RequestBody StatusRequest request) {
        return service.setStatus(id, request.status(), request.note());
    }

    /**
     * The gated {@code RECONCILING -> RECONCILED} submit (R10): advances only when
     * every commitment is statused, else 422 with the plan unchanged.
     */
    @PostMapping("/api/lifecycle/plans/{planId}/transitions/submit-reconciled")
    public WeeklyPlanDto submitReconciled(@PathVariable UUID planId) {
        return service.submit(planId);
    }
}
