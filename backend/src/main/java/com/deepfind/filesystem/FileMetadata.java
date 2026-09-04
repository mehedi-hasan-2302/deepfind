package com.deepfind.filesystem;

import java.nio.file.Path;
import java.time.Instant;

public record FileMetadata(
        Path absolutePath,
        String normalizedPath,
        String filename,
        String extension,
        FileSystemEntryKind kind,
        long sizeBytes,
        Instant modifiedAt,
        Instant createdAt) {}
