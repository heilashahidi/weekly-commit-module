package com.weeklycommit.rcdo;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only REST API for the RCDO strategy hierarchy. JWT-secured by default
 * (only {@code /health} is public per SecurityConfig). Workstream C uses
 * {@code /nodes/{id}} to validate a commitment's link target.
 */
@RestController
@RequestMapping("/api/rcdo")
public class RcdoController {

    private final RcdoService service;

    public RcdoController(RcdoService service) {
        this.service = service;
    }

    /** The full hierarchy as nested root nodes. */
    @GetMapping("/tree")
    public List<RcdoNodeDto> tree() {
        return service.getTree();
    }

    /** A single node with its direct children, or 404 if no such node. */
    @GetMapping("/nodes/{id}")
    public RcdoNodeDto node(@PathVariable UUID id) {
        return service.getNode(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RCDO node not found"));
    }
}
