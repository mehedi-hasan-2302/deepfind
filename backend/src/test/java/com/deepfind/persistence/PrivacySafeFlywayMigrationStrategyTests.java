package com.deepfind.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepfind.testing.LogCapture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PrivacySafeFlywayMigrationStrategyTests {

    @Test
    void replacesMigrationFailureDetailsWithASafeEventAndException() {
        String privateFailure = "jdbc:sqlite:C:\\Users\\person\\Private Files\\deepfind.db secret-query";
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(new IllegalStateException(privateFailure));

        try (LogCapture logs = LogCapture.forClass(PrivacySafeFlywayMigrationStrategy.class)) {
            assertThatThrownBy(() -> new PrivacySafeFlywayMigrationStrategy().migrate(flyway))
                    .isInstanceOf(PersistenceAccessException.class)
                    .hasMessage("DeepFind could not migrate its local database.")
                    .hasNoCause();

            assertThat(logs.messages())
                    .containsExactly("event=database_migration_failed exception=IllegalStateException")
                    .allSatisfy(message ->
                            assertThat(message).doesNotContain(privateFailure, "Private Files", "secret-query"));
        }
    }
}
