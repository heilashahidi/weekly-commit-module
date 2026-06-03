package com.weeklycommit.lifecycle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for weekly commitments. JWT-secured by default (only {@code /health}
 * is public per SecurityConfig). Thin — every rule (RCDO link, immutability,
 * ownership) lives in {@link CommitmentService}; this layer only maps HTTP to
 * service calls and the response record.
 *
 * <p>Routing: commitments are created under their plan
 * ({@code POST /api/plans/{planId}/commitments}) and addressed directly once they
 * exist ({@code PUT}/{@code DELETE /api/commitments/{id}}) — the plan is implied
 * by the commitment, so the edit/delete paths don't repeat it.
 */
@RestController
public class CommitmentController {

    private final CommitmentService service;

    public CommitmentController(CommitmentService service) {
        this.service = service;
    }

    /** Request body for create/edit: the strategy link and the commitment title. */
    public record CommitmentRequest(UUID rcdoNodeId, String title) {}

    @PostMapping("/api/plans/{planId}/commitments")
    public ResponseEntity<CommitmentDto> create(
            @PathVariable UUID planId, @RequestBody CommitmentRequest request) {
        CommitmentDto created =
            service.create(planId, request.rcdoNodeId(), request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/commitments/{id}")
    public CommitmentDto edit(
            @PathVariable UUID id, @RequestBody CommitmentRequest request) {
        return service.edit(id, request.rcdoNodeId(), request.title());
    }

    @DeleteMapping("/api/commitments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
