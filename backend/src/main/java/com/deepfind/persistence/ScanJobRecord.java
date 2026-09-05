package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record ScanJobRecord(
        UUID jobId,
        Path root,
        ScanJobState state,
        Path currentPath,
        ScanJobMetrics metrics,
        ScanFailureRecord lastFailure,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
