package com.deepfind.filesystem;

import java.nio.file.Path;

public record DiscoverySummary(
        Path root,
        long entriesDiscovered,
        long filesDiscovered,
        long directoriesDiscovered,
        long symbolicLinksDiscovered,
        long otherEntriesDiscovered,
        long entriesSkipped,
        long failures) {}
