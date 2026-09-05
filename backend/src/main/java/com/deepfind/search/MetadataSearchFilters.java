package com.deepfind.search;

import com.deepfind.filesystem.FileSystemEntryKind;
import java.time.Instant;
import java.util.Locale;

public record MetadataSearchFilters(
        FileSystemEntryKind kind,
        String extension,
        Instant modifiedAfter,
        Instant modifiedBefore,
        Long minSizeBytes,
        Long maxSizeBytes) {

    public MetadataSearchFilters {
        extension = normalizeExtension(extension);
        if (modifiedAfter != null && modifiedBefore != null && modifiedAfter.isAfter(modifiedBefore)) {
            throw new IllegalArgumentException("modifiedAfter must not be after modifiedBefore");
        }
        if ((minSizeBytes != null && minSizeBytes < 0) || (maxSizeBytes != null && maxSizeBytes < 0)) {
            throw new IllegalArgumentException("file sizes must not be negative");
        }
        if (minSizeBytes != null && maxSizeBytes != null && minSizeBytes > maxSizeBytes) {
            throw new IllegalArgumentException("minSizeBytes must not exceed maxSizeBytes");
        }
    }

    public static MetadataSearchFilters none() {
        return new MetadataSearchFilters(null, null, null, null, null, null);
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
