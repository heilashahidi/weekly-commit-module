package com.weeklycommit.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for carry-forward (U8, KTD 1, R12–R15). JWT-secured by default (only
 * {@code /health} is public per SecurityConfig). Thin — candidate selection,
 * lineage, ownership, and the RECONCILED gate all live in
 * {@link CarryForwardService}; this layer maps HTTP to service calls.
 *
 * <p>Routing under the {@code /api/lifecycle} namespace, addressed by the source
 * (reconciled) plan: {@code GET .../carry-candidates} lists what can roll over;
 * {@code POST .../carry} seeds the IC-selected subset into next week's draft.
 */
@RestController
public class CarryForwardController {

    private final CarryForwardService service;

    public CarryForwardController(CarryForwardService service) {
        this.service = service;
    }

    /** Request body: the IC-selected subset of candidate commitment ids to carry. */
    public record CarryRequest(List<UUID> commitmentIds) {}

    /** Lists carry candidates (PARTIAL/NOT_DONE planned) for a RECONCILED plan. */
    @GetMapping("/api/lifecycle/plans/{planId}/carry-candidates")
    public List<CarryCandidateDto> candidates(@PathVariable UUID planId) {
        return service.listCandidates(planId);
    }

    /** Seeds the selected candidates into next week's DRAFT with lineage (R13/R14). */
    @PostMapping("/api/lifecycle/plans/{planId}/carry")
    public List<CommitmentDto> carry(
            @PathVariable UUID planId, @RequestBody CarryRequest request) {
        return service.carry(planId, request.commitmentIds());
    }
}
