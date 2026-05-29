package com.weeklycommit.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint — the backend half of the walking skeleton. Returns
 * only {@code {"status":"UP"}}; no DB, version, or migration internals are
 * exposed (minimal public surface).
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
