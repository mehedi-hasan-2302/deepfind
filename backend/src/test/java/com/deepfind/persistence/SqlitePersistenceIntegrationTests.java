package com.deepfind.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

class SqlitePersistenceIntegrationTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesAnExistingVersionOneDatabaseToTheLatestSchema() {
        DataSource dataSource = dataSource(temporaryDirectory.resolve("migration.db"));
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(tableCount(jdbc, "indexed_roots")).isOne();
        assertThat(tableCount(jdbc, "application_settings")).isZero();

        migrate(dataSource);
        migrate(dataSource);

        assertThat(tableCount(jdbc, "indexed_roots")).isOne();
        assertThat(tableCount(jdbc, "application_settings")).isOne();
        assertThat(tableCount(jdbc, "scan_jobs")).isOne();
        assertThat(tableCount(jdbc, "scan_failures")).isOne();
    }

    @Test
    void restoresUnicodeRootsAcrossRepositoryInstancesWithoutDuplicates() {
        DataSource dataSource = dataSource(temporaryDirectory.resolve("restart.db"));
        migrate(dataSource);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        PersistentRootCatalog first = catalog(jdbc);
        Path root = temporaryDirectory.resolve("資料/প্রকল্প").toAbsolutePath().normalize();
        Instant firstSelection = Instant.parse("2026-09-04T08:00:00Z");
        Instant secondSelection = Instant.parse("2026-09-04T09:00:00Z");
        Instant indexedAt = Instant.parse("2026-09-04T09:05:00Z");

        first.rememberSelected(root, firstSelection);
        first.rememberSelected(root, secondSelection);
        first.markIndexed(root, indexedAt);

        PersistentRootCatalog restarted =
                catalog(JdbcClient.create(dataSource(temporaryDirectory.resolve("restart.db"))));
        assertThat(restarted.lastSelectedRoot()).contains(root);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM indexed_roots")
                        .query(Long.class)
                        .single())
                .isOne();

        IndexedRootRecord record = new SqliteIndexedRootRepository(jdbc)
                .findByPathKey(com.deepfind.filesystem.PathNormalizer.searchKey(root))
                .orElseThrow();
        assertThat(record.createdAt()).isEqualTo(firstSelection);
        assertThat(record.lastSelectedAt()).isEqualTo(secondSelection);
        assertThat(record.lastIndexedAt()).isEqualTo(indexedAt);
    }

    @Test
    void refusesToReplaceACorruptDatabase() throws Exception {
        Path database = temporaryDirectory.resolve("corrupt.db");
        Files.writeString(database, "this is not a SQLite database");

        assertThatThrownBy(() -> migrate(dataSource(database))).isInstanceOf(FlywayException.class);
        assertThat(Files.readString(database)).isEqualTo("this is not a SQLite database");
    }

    @Test
    void persistsHistoryFailuresAndRecoversAnInterruptedScan() {
        Path database = temporaryDirectory.resolve("history.db");
        DataSource dataSource = dataSource(database);
        migrate(dataSource);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Path root = temporaryDirectory.resolve("কাজ/資料").toAbsolutePath().normalize();
        Instant selectedAt = Instant.parse("2026-09-05T01:00:00Z");
        catalog(jdbc).rememberSelected(root, selectedAt);
        UUID jobId = UUID.randomUUID();
        SqliteScanHistoryRepository history = new SqliteScanHistoryRepository(jdbc);
        history.start(jobId, root, selectedAt);
        ScanJobMetrics checkpoint = new ScanJobMetrics(250, 200, 45, 3, 2, 4, 1, 225);
        history.checkpoint(jobId, root.resolve("partial"), checkpoint);
        history.recordFailure(
                jobId,
                root.resolve("locked"),
                "PERMISSION_DENIED",
                "DeepFind could not read this path.",
                Instant.parse("2026-09-05T01:01:00Z"));

        SqliteScanHistoryRepository restarted =
                new SqliteScanHistoryRepository(JdbcClient.create(dataSource(database)));
        Instant interruptedAt = Instant.parse("2026-09-05T01:02:00Z");
        ScanJobRecord recovered = restarted
                .interruptRunningJobs(interruptedAt, "The previous indexing run was interrupted.")
                .orElseThrow();

        assertThat(recovered.jobId()).isEqualTo(jobId);
        assertThat(recovered.root()).isEqualTo(root);
        assertThat(recovered.state()).isEqualTo(ScanJobState.INTERRUPTED);
        assertThat(recovered.metrics()).isEqualTo(checkpoint);
        assertThat(recovered.lastFailure().reason()).isEqualTo("PERMISSION_DENIED");
        assertThat(recovered.finishedAt()).isEqualTo(interruptedAt);
        assertThat(restarted.interruptRunningJobs(interruptedAt.plusSeconds(1), "again"))
                .isEmpty();
        assertThat(restarted.findRecent(20)).containsExactly(recovered);
    }

    private static PersistentRootCatalog catalog(JdbcClient jdbc) {
        return new PersistentRootCatalog(
                new SqliteIndexedRootRepository(jdbc), new SqliteApplicationSettingsRepository(jdbc));
    }

    private static long tableCount(JdbcClient jdbc, String table) {
        return jdbc.sql("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = :name")
                .param("name", table)
                .query(Long.class)
                .single();
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static DataSource dataSource(Path database) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + database.toAbsolutePath().normalize());
        return dataSource;
    }
}
