package com.weeklycommit.health;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint — the backend half of the walking skeleton. Reflects
 * real readiness by probing the datasource: returns 200 {@code {"status":"UP"}}
 * when a connection is available, 503 {@code {"status":"DOWN"}} otherwise. No DB
 * version, migration, or error internals are exposed (minimal public surface).
 */
@RestController
public class HealthController {

    /** Seconds the driver may take to validate the connection. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 1;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (databaseReachable()) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (Exception e) {
            // Swallow the cause on purpose: the public endpoint must not leak
            // datasource details. The DOWN status is the signal.
            return false;
        }
    }
}
