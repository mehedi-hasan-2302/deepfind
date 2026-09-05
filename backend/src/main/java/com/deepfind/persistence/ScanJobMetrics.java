package com.deepfind.persistence;

public record ScanJobMetrics(
        long entriesDiscovered,
        long filesDiscovered,
        long directoriesDiscovered,
        long symbolicLinksDiscovered,
        long otherEntriesDiscovered,
        long entriesSkipped,
        long failures,
        long entriesIndexed) {

    public static ScanJobMetrics empty() {
        return new ScanJobMetrics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
