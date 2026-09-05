package com.deepfind.jobs;

import com.deepfind.filesystem.DiscoveryFailure;
import com.deepfind.filesystem.DiscoveryProgress;
import com.deepfind.index.MetadataIndexingOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record IndexingJobStatus(
        UUID jobId,
        IndexingJobState state,
        Path root,
        Path currentPath,
        long entriesDiscovered,
        long filesDiscovered,
        long directoriesDiscovered,
        long symbolicLinksDiscovered,
        long otherEntriesDiscovered,
        long entriesSkipped,
        long failures,
        long entriesIndexed,
        DiscoveryFailure lastFailure,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    static IndexingJobStatus idle() {
        return idle(null);
    }

    static IndexingJobStatus idle(Path root) {
        return new IndexingJobStatus(
                null, IndexingJobState.IDLE, root, null, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null);
    }

    static IndexingJobStatus running(UUID jobId, Path root, Instant startedAt) {
        return new IndexingJobStatus(
                jobId, IndexingJobState.RUNNING, root, root, 0, 0, 0, 0, 0, 0, 0, 0, null, null, startedAt, null);
    }

    IndexingJobStatus withProgress(DiscoveryProgress progress, long indexed) {
        return new IndexingJobStatus(
                jobId,
                state,
                root,
                progress.currentPath(),
                progress.entriesDiscovered(),
                progress.filesDiscovered(),
                progress.directoriesDiscovered(),
                progress.symbolicLinksDiscovered(),
                progress.otherEntriesDiscovered(),
                progress.entriesSkipped(),
                progress.failures(),
                indexed,
                lastFailure,
                errorMessage,
                startedAt,
                finishedAt);
    }

    IndexingJobStatus withFailure(DiscoveryFailure failure) {
        return new IndexingJobStatus(
                jobId,
                state,
                root,
                failure.path(),
                entriesDiscovered,
                filesDiscovered,
                directoriesDiscovered,
                symbolicLinksDiscovered,
                otherEntriesDiscovered,
                entriesSkipped,
                failures,
                entriesIndexed,
                failure,
                errorMessage,
                startedAt,
                finishedAt);
    }

    IndexingJobStatus completed(MetadataIndexingOutcome outcome, Instant completedAt) {
        var discovery = outcome.discovery();
        return new IndexingJobStatus(
                jobId,
                IndexingJobState.COMPLETED,
                root,
                currentPath,
                discovery.entriesDiscovered(),
                discovery.filesDiscovered(),
                discovery.directoriesDiscovered(),
                discovery.symbolicLinksDiscovered(),
                discovery.otherEntriesDiscovered(),
                discovery.entriesSkipped(),
                discovery.failures(),
                outcome.entriesIndexed(),
                lastFailure,
                null,
                startedAt,
                completedAt);
    }

    IndexingJobStatus failed(String message, Instant failedAt) {
        return new IndexingJobStatus(
                jobId,
                IndexingJobState.FAILED,
                root,
                currentPath,
                entriesDiscovered,
                filesDiscovered,
                directoriesDiscovered,
                symbolicLinksDiscovered,
                otherEntriesDiscovered,
                entriesSkipped,
                failures,
                entriesIndexed,
                lastFailure,
                message,
                startedAt,
                failedAt);
    }
}
