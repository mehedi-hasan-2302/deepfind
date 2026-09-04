package com.deepfind.api.index;

import com.deepfind.jobs.IndexingJobStatus;
import java.time.Instant;
import java.util.UUID;

public record IndexStatusResponse(
        UUID jobId,
        String state,
        String root,
        String currentPath,
        long entriesDiscovered,
        long filesDiscovered,
        long directoriesDiscovered,
        long symbolicLinksDiscovered,
        long otherEntriesDiscovered,
        long entriesSkipped,
        long failures,
        long entriesIndexed,
        IndexFailureResponse lastFailure,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    static IndexStatusResponse from(IndexingJobStatus status) {
        return new IndexStatusResponse(
                status.jobId(),
                status.state().name(),
                status.root() == null ? null : status.root().toString(),
                status.currentPath() == null ? null : status.currentPath().toString(),
                status.entriesDiscovered(),
                status.filesDiscovered(),
                status.directoriesDiscovered(),
                status.symbolicLinksDiscovered(),
                status.otherEntriesDiscovered(),
                status.entriesSkipped(),
                status.failures(),
                status.entriesIndexed(),
                IndexFailureResponse.from(status.lastFailure()),
                status.errorMessage(),
                status.startedAt(),
                status.finishedAt());
    }
}
