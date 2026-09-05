package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanHistoryRepository {

    Optional<ScanJobRecord> interruptRunningJobs(Instant interruptedAt, String message);

    void start(UUID jobId, Path root, Instant startedAt);

    void checkpoint(UUID jobId, Path currentPath, ScanJobMetrics metrics);

    void requestPause(UUID jobId, Path currentPath, ScanJobMetrics metrics);

    void markPaused(UUID jobId, Path currentPath, ScanJobMetrics metrics, String message, Instant pausedAt);

    void recordFailure(UUID jobId, Path path, String reason, String message, Instant recordedAt);

    void finish(UUID jobId, ScanJobState state, ScanJobMetrics metrics, String errorMessage, Instant finishedAt);

    List<ScanJobRecord> findRecent(int limit);
}
