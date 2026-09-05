package com.deepfind.persistence;

import com.deepfind.filesystem.PathNormalizer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SqliteScanHistoryRepository implements ScanHistoryRepository {

    private static final String SELECT_JOBS = """
            SELECT
                j.job_id,
                r.absolute_path AS root_path,
                j.state,
                j.current_path,
                j.entries_discovered,
                j.files_discovered,
                j.directories_discovered,
                j.symbolic_links_discovered,
                j.other_entries_discovered,
                j.entries_skipped,
                j.failures,
                j.entries_indexed,
                j.error_message,
                j.started_at,
                j.finished_at,
                f.failure_id,
                f.absolute_path AS failure_path,
                f.reason AS failure_reason,
                f.message AS failure_message,
                f.recorded_at AS failure_recorded_at
            FROM scan_jobs j
            JOIN indexed_roots r ON r.path_key = j.root_path_key
            LEFT JOIN scan_failures f ON f.failure_id = (
                SELECT MAX(latest.failure_id) FROM scan_failures latest WHERE latest.job_id = j.job_id
            )
            """;

    private final JdbcClient jdbc;

    public SqliteScanHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<ScanJobRecord> interruptRunningJobs(Instant interruptedAt, String message) {
        int updated = jdbc.sql("""
                        UPDATE scan_jobs
                        SET state = 'INTERRUPTED', finished_at = :interruptedAt, error_message = :message
                        WHERE state = 'RUNNING'
                        """)
                .param("interruptedAt", interruptedAt.toString())
                .param("message", message)
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbc.sql(SELECT_JOBS + " WHERE j.state = 'INTERRUPTED' AND j.finished_at = :interruptedAt"
                        + " ORDER BY j.started_at DESC LIMIT 1")
                .param("interruptedAt", interruptedAt.toString())
                .query(this::mapJob)
                .optional();
    }

    @Override
    public void start(UUID jobId, Path root, Instant startedAt) {
        jdbc.sql("""
                        INSERT INTO scan_jobs (
                            job_id, root_path_key, state, current_path, started_at
                        ) VALUES (:jobId, :rootPathKey, 'RUNNING', :currentPath, :startedAt)
                        """)
                .param("jobId", jobId.toString())
                .param("rootPathKey", PathNormalizer.searchKey(root))
                .param("currentPath", PathNormalizer.absolute(root).toString())
                .param("startedAt", startedAt.toString())
                .update();
    }

    @Override
    public void checkpoint(UUID jobId, Path currentPath, ScanJobMetrics metrics) {
        updateMetrics("RUNNING", jobId, currentPath, metrics, null, null);
    }

    @Override
    public void recordFailure(UUID jobId, Path path, String reason, String message, Instant recordedAt) {
        jdbc.sql("""
                        INSERT INTO scan_failures (job_id, absolute_path, reason, message, recorded_at)
                        VALUES (:jobId, :absolutePath, :reason, :message, :recordedAt)
                        """)
                .param("jobId", jobId.toString())
                .param("absolutePath", PathNormalizer.absolute(path).toString())
                .param("reason", reason)
                .param("message", message)
                .param("recordedAt", recordedAt.toString())
                .update();
    }

    @Override
    public void finish(
            UUID jobId, ScanJobState state, ScanJobMetrics metrics, String errorMessage, Instant finishedAt) {
        if (state != ScanJobState.COMPLETED && state != ScanJobState.FAILED) {
            throw new IllegalArgumentException("A finished scan must be completed or failed.");
        }
        int updated = updateMetrics(state.name(), jobId, null, metrics, errorMessage, finishedAt);
        if (updated != 1) {
            throw new PersistenceAccessException("DeepFind could not find the running scan job to finish.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScanJobRecord> findRecent(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("History limit must be between 1 and 100.");
        }
        return jdbc.sql(SELECT_JOBS + " ORDER BY j.started_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    private int updateMetrics(
            String state,
            UUID jobId,
            Path currentPath,
            ScanJobMetrics metrics,
            String errorMessage,
            Instant finishedAt) {
        return jdbc.sql("""
                        UPDATE scan_jobs SET
                            state = :state,
                            current_path = COALESCE(:currentPath, current_path),
                            entries_discovered = :entriesDiscovered,
                            files_discovered = :filesDiscovered,
                            directories_discovered = :directoriesDiscovered,
                            symbolic_links_discovered = :symbolicLinksDiscovered,
                            other_entries_discovered = :otherEntriesDiscovered,
                            entries_skipped = :entriesSkipped,
                            failures = :failures,
                            entries_indexed = :entriesIndexed,
                            error_message = :errorMessage,
                            finished_at = :finishedAt
                        WHERE job_id = :jobId AND state = 'RUNNING'
                        """)
                .param("state", state)
                .param("currentPath", currentPath == null ? null : currentPath.toString())
                .param("entriesDiscovered", metrics.entriesDiscovered())
                .param("filesDiscovered", metrics.filesDiscovered())
                .param("directoriesDiscovered", metrics.directoriesDiscovered())
                .param("symbolicLinksDiscovered", metrics.symbolicLinksDiscovered())
                .param("otherEntriesDiscovered", metrics.otherEntriesDiscovered())
                .param("entriesSkipped", metrics.entriesSkipped())
                .param("failures", metrics.failures())
                .param("entriesIndexed", metrics.entriesIndexed())
                .param("errorMessage", errorMessage)
                .param("finishedAt", finishedAt == null ? null : finishedAt.toString())
                .param("jobId", jobId.toString())
                .update();
    }

    private ScanJobRecord mapJob(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new ScanJobRecord(
                UUID.fromString(resultSet.getString("job_id")),
                Path.of(resultSet.getString("root_path")),
                ScanJobState.valueOf(resultSet.getString("state")),
                nullablePath(resultSet.getString("current_path")),
                new ScanJobMetrics(
                        resultSet.getLong("entries_discovered"),
                        resultSet.getLong("files_discovered"),
                        resultSet.getLong("directories_discovered"),
                        resultSet.getLong("symbolic_links_discovered"),
                        resultSet.getLong("other_entries_discovered"),
                        resultSet.getLong("entries_skipped"),
                        resultSet.getLong("failures"),
                        resultSet.getLong("entries_indexed")),
                nullableFailure(resultSet),
                resultSet.getString("error_message"),
                Instant.parse(resultSet.getString("started_at")),
                nullableInstant(resultSet.getString("finished_at")));
    }

    private static ScanFailureRecord nullableFailure(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        long id = resultSet.getLong("failure_id");
        if (resultSet.wasNull()) {
            return null;
        }
        return new ScanFailureRecord(
                id,
                Path.of(resultSet.getString("failure_path")),
                resultSet.getString("failure_reason"),
                resultSet.getString("failure_message"),
                Instant.parse(resultSet.getString("failure_recorded_at")));
    }

    private static Path nullablePath(String value) {
        return value == null ? null : Path.of(value);
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
