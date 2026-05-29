package com.weeklycommit.support;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for integration tests that need a real PostgreSQL. Runs an embedded
 * Postgres as a local process (Zonky) — no Docker daemon required — and replaces
 * the application datasource with it. Flyway and JPA run against genuine Postgres.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY, refresh = RefreshMode.AFTER_CLASS)
public abstract class AbstractPostgresIT {
}
