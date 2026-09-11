package com.deepfind.persistence;

import static com.deepfind.diagnostics.PrivacySafeDiagnostics.exceptionType;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

@Component
public final class PrivacySafeFlywayMigrationStrategy implements FlywayMigrationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivacySafeFlywayMigrationStrategy.class);

    @Override
    public void migrate(Flyway flyway) {
        try {
            flyway.migrate();
            LOGGER.info("event=database_migration_completed");
        } catch (RuntimeException exception) {
            LOGGER.error("event=database_migration_failed exception={}", exceptionType(exception));
            throw new PersistenceAccessException("DeepFind could not migrate its local database.");
        }
    }
}
