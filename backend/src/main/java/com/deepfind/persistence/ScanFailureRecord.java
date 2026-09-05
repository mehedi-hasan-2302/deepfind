package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;

public record ScanFailureRecord(long id, Path path, String reason, String message, Instant recordedAt) {}
