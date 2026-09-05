package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;

public record IndexedRootRecord(
        String pathKey,
        Path absolutePath,
        boolean enabled,
        Instant createdAt,
        Instant lastSelectedAt,
        Instant lastIndexedAt) {}
