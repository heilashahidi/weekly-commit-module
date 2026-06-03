package com.weeklycommit.lifecycle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the non-blocking manager-review annotation (U9, R16/R17). JWT-secured
 * by default (only {@code /health} is public per SecurityConfig). Thin — the upsert
 * semantics, plan-existence check, and reviewer stamping live in
 * {@link ManagerReviewService}; this layer maps HTTP to service calls.
 *
 * <p>Routing: a review is addressed under its plan in the {@code /api/lifecycle}
 * namespace. {@code PUT .../review} creates-or-updates the single review (the upsert
 * verb fits one-review-per-plan); {@code GET .../review} fetches it, returning 204 No
 * Content when the plan has no review yet (its absence is a valid state — R17).
 */
@RestController
public class ManagerReviewController {

    private final ManagerReviewService service;

    public ManagerReviewController(ManagerReviewService service) {
        this.service = service;
    }

    /** Request body for create/update: the review comment. */
    public record ReviewRequest(String comment) {}

    @PutMapping("/api/lifecycle/plans/{planId}/review")
    public ManagerReviewDto upsert(
            @PathVariable UUID planId, @RequestBody ReviewRequest request) {
        return service.upsertReview(planId, request.comment());
    }

    @GetMapping("/api/lifecycle/plans/{planId}/review")
    public ResponseEntity<ManagerReviewDto> get(@PathVariable UUID planId) {
        return service
            .getReview(planId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
