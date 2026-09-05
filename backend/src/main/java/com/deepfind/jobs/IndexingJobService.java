package com.deepfind.jobs;

import com.deepfind.filesystem.DiscoveryFailure;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoveryProgress;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.index.IndexWatchLifecycle;
import com.deepfind.index.IndexingPausedException;
import com.deepfind.index.MetadataIndexingOutcome;
import com.deepfind.index.MetadataIndexingService;
import com.deepfind.index.MetadataReconciliationService;
import com.deepfind.persistence.RootCatalog;
import com.deepfind.persistence.ScanHistoryRepository;
import com.deepfind.persistence.ScanJobMetrics;
import com.deepfind.persistence.ScanJobState;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IndexingJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexingJobService.class);
    private static final long CHECKPOINT_ENTRY_INTERVAL = 250;
    private static final Duration CHECKPOINT_TIME_INTERVAL = Duration.ofSeconds(2);
    private static final String FAILED_MESSAGE =
            "Indexing stopped because the local search index could not be updated.";
    private static final String INTERRUPTED_MESSAGE =
            "The previous indexing run was interrupted. Start indexing again to reconcile this folder.";
    private static final String PAUSED_MESSAGE =
            "Indexing was paused safely. Resume to reconcile the folder from its current filesystem state.";

    private final MetadataIndexingService indexingService;
    private final MetadataReconciliationService reconciliationService;
    private final RootCatalog rootCatalog;
    private final ScanHistoryRepository scanHistory;
    private final IndexWatchLifecycle watchLifecycle;
    private final Clock clock;
    private final ExecutorService executor;
    private final AtomicReference<IndexingJobStatus> status;
    private final AtomicReference<UUID> pauseRequestedJob = new AtomicReference<>();

    @Autowired
    public IndexingJobService(
            MetadataIndexingService indexingService,
            RootCatalog rootCatalog,
            ScanHistoryRepository scanHistory,
            IndexWatchLifecycle watchLifecycle,
            MetadataReconciliationService reconciliationService) {
        this(
                indexingService,
                reconciliationService,
                rootCatalog,
                scanHistory,
                watchLifecycle,
                Clock.systemUTC(),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "deepfind-indexer");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    IndexingJobService(
            MetadataIndexingService indexingService,
            RootCatalog rootCatalog,
            ScanHistoryRepository scanHistory,
            Clock clock,
            ExecutorService executor) {
        this(indexingService, null, rootCatalog, scanHistory, ignoredLifecycle(), clock, executor);
    }

    IndexingJobService(
            MetadataIndexingService indexingService,
            RootCatalog rootCatalog,
            ScanHistoryRepository scanHistory,
            IndexWatchLifecycle watchLifecycle,
            Clock clock,
            ExecutorService executor) {
        this(indexingService, null, rootCatalog, scanHistory, watchLifecycle, clock, executor);
    }

    IndexingJobService(
            MetadataIndexingService indexingService,
            MetadataReconciliationService reconciliationService,
            RootCatalog rootCatalog,
            ScanHistoryRepository scanHistory,
            IndexWatchLifecycle watchLifecycle,
            Clock clock,
            ExecutorService executor) {
        this.indexingService = Objects.requireNonNull(indexingService, "indexingService must not be null");
        this.reconciliationService = reconciliationService;
        this.rootCatalog = Objects.requireNonNull(rootCatalog, "rootCatalog must not be null");
        this.scanHistory = Objects.requireNonNull(scanHistory, "scanHistory must not be null");
        this.watchLifecycle = Objects.requireNonNull(watchLifecycle, "watchLifecycle must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        var recovered = scanHistory.interruptRunningJobs(clock.instant(), INTERRUPTED_MESSAGE);
        if (recovered.isEmpty()) {
            recovered = scanHistory.findRecent(1).stream()
                    .filter(record -> record.state() == ScanJobState.PAUSED)
                    .findFirst();
        }
        this.status = new AtomicReference<>(recovered
                .map(IndexingJobStatus::fromRecord)
                .orElseGet(() ->
                        IndexingJobStatus.idle(rootCatalog.lastSelectedRoot().orElse(null))));
    }

    public synchronized IndexingJobStatus start(Path requestedRoot) {
        IndexingJobStatus current = status.get();
        if (isActive(current.state())) {
            throw new IndexingAlreadyRunningException();
        }

        Path root = PathNormalizer.absolute(requestedRoot);
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IndexRootNotAccessibleException("DeepFind cannot read this folder.");
        }

        return schedule(root, true, false);
    }

    public synchronized boolean reconcileSelectedRootIfIdle() {
        if (isActive(status.get().state()) || reconciliationService == null) {
            return false;
        }
        Path root = rootCatalog.lastSelectedRoot().orElse(null);
        if (root == null || !Files.isDirectory(root) || !Files.isReadable(root)) {
            return false;
        }
        schedule(PathNormalizer.absolute(root), false, true);
        return true;
    }

    public synchronized IndexingJobStatus reconcileSelectedRoot() {
        if (isActive(status.get().state())) {
            throw new IndexingAlreadyRunningException();
        }
        Path root = rootCatalog.lastSelectedRoot().orElseThrow(NoIndexRootSelectedException::new);
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IndexRootNotAccessibleException("DeepFind cannot read the selected folder.");
        }
        if (reconciliationService == null) {
            throw new IllegalStateException("Reconciliation is unavailable.");
        }
        return schedule(PathNormalizer.absolute(root), false, true);
    }

    public synchronized IndexingJobStatus pause() {
        IndexingJobStatus current = status.get();
        if (current.state() != IndexingJobState.RUNNING) {
            throw new IndexingJobStateConflictException("Only a running indexing job can be paused.");
        }
        scanHistory.requestPause(current.jobId(), current.currentPath(), metrics(current));
        pauseRequestedJob.set(current.jobId());
        IndexingJobStatus pausing = current.pausing();
        status.set(pausing);
        return pausing;
    }

    public synchronized IndexingJobStatus resume() {
        IndexingJobStatus current = status.get();
        if (current.state() != IndexingJobState.PAUSED) {
            throw new IndexingJobStateConflictException("Only a paused indexing job can be resumed.");
        }
        if (!Files.isDirectory(current.root()) || !Files.isReadable(current.root())) {
            throw new IndexRootNotAccessibleException("DeepFind cannot read the selected folder.");
        }
        if (reconciliationService == null) {
            throw new IllegalStateException("Reconciliation is unavailable.");
        }
        return schedule(current.root(), false, true);
    }

    private IndexingJobStatus schedule(Path root, boolean rememberSelection, boolean reconciliation) {
        Instant startedAt = clock.instant();
        if (rememberSelection) {
            rootCatalog.rememberSelected(root, startedAt);
        }
        UUID jobId = UUID.randomUUID();
        scanHistory.start(jobId, root, startedAt);
        watchLifecycle.pause();
        IndexingJobStatus started = IndexingJobStatus.running(jobId, root, startedAt);
        status.set(started);
        executor.submit(() -> run(started, reconciliation));
        return started;
    }

    public IndexingJobStatus status() {
        return status.get();
    }

    public List<IndexingJobStatus> history(int limit) {
        return scanHistory.findRecent(limit).stream()
                .map(IndexingJobStatus::fromRecord)
                .toList();
    }

    private void run(IndexingJobStatus started, boolean reconciliation) {
        AtomicLong indexed = new AtomicLong();
        AtomicLong lastCheckpointEntries = new AtomicLong();
        AtomicReference<Instant> lastCheckpointAt = new AtomicReference<>(started.startedAt());
        try {
            DiscoveryObserver observer = new DiscoveryObserver() {
                @Override
                public void onEntry(FileMetadata metadata) {
                    indexed.incrementAndGet();
                }

                @Override
                public void onFailure(DiscoveryFailure failure) {
                    status.updateAndGet(current -> current.withFailure(failure));
                    scanHistory.recordFailure(
                            started.jobId(),
                            failure.path(),
                            failure.reason().name(),
                            failure.message(),
                            clock.instant());
                }

                @Override
                public void onProgress(DiscoveryProgress progress) {
                    long indexedEntries = indexed.get();
                    status.updateAndGet(current -> current.withProgress(progress, indexedEntries));
                    if (started.jobId().equals(pauseRequestedJob.get())) {
                        throw new IndexingPausedException();
                    }
                    Instant now = clock.instant();
                    if (shouldCheckpoint(
                            progress.entriesDiscovered(), lastCheckpointEntries.get(), now, lastCheckpointAt.get())) {
                        scanHistory.checkpoint(
                                started.jobId(), progress.currentPath(), metrics(progress, indexedEntries));
                        lastCheckpointEntries.set(progress.entriesDiscovered());
                        lastCheckpointAt.set(now);
                    }
                }
            };
            MetadataIndexingOutcome outcome = reconciliation
                    ? reconciliationService.reconcileRoot(started.root(), ExclusionPolicy.defaults(), observer)
                    : indexingService.indexRoot(started.root(), ExclusionPolicy.defaults(), observer);
            synchronized (this) {
                var finishedAt = clock.instant();
                pauseRequestedJob.compareAndSet(started.jobId(), null);
                rootCatalog.markIndexed(started.root(), finishedAt);
                scanHistory.finish(started.jobId(), ScanJobState.COMPLETED, metrics(outcome), null, finishedAt);
                status.updateAndGet(current -> current.completed(outcome, finishedAt));
            }
        } catch (IndexingPausedException exception) {
            synchronized (this) {
                pauseRequestedJob.compareAndSet(started.jobId(), null);
                IndexingJobStatus current = status.get();
                if (started.jobId().equals(current.jobId()) && current.state() == IndexingJobState.PAUSING) {
                    Instant pausedAt = clock.instant();
                    IndexingJobStatus paused = current.paused(PAUSED_MESSAGE, pausedAt);
                    scanHistory.markPaused(
                            started.jobId(), paused.currentPath(), metrics(paused), PAUSED_MESSAGE, pausedAt);
                    status.set(paused);
                }
            }
        } catch (RuntimeException exception) {
            synchronized (this) {
                Instant failedAt = clock.instant();
                IndexingJobStatus failed = status.get().failed(FAILED_MESSAGE, failedAt);
                LOGGER.error(
                        "Indexing job {} failed with {}.",
                        started.jobId(),
                        exception.getClass().getSimpleName());
                try {
                    scanHistory.finish(started.jobId(), ScanJobState.FAILED, metrics(failed), FAILED_MESSAGE, failedAt);
                } catch (RuntimeException persistenceException) {
                    LOGGER.error(
                            "Indexing job {} failure state could not be persisted: {}.",
                            started.jobId(),
                            persistenceException.getClass().getSimpleName());
                } finally {
                    status.set(failed);
                }
            }
        } finally {
            try {
                watchLifecycle.watch(started.root());
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Filesystem change tracking could not resume after indexing job {}: {}.",
                        started.jobId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private static IndexWatchLifecycle ignoredLifecycle() {
        return new IndexWatchLifecycle() {
            @Override
            public void pause() {}

            @Override
            public void watch(Path root) {}
        };
    }

    private static boolean shouldCheckpoint(long entries, long previousEntries, Instant now, Instant previousAt) {
        return entries - previousEntries >= CHECKPOINT_ENTRY_INTERVAL
                || Duration.between(previousAt, now).compareTo(CHECKPOINT_TIME_INTERVAL) >= 0;
    }

    private static boolean isActive(IndexingJobState state) {
        return state == IndexingJobState.RUNNING
                || state == IndexingJobState.PAUSING
                || state == IndexingJobState.PAUSED;
    }

    private static ScanJobMetrics metrics(DiscoveryProgress progress, long indexedEntries) {
        return new ScanJobMetrics(
                progress.entriesDiscovered(),
                progress.filesDiscovered(),
                progress.directoriesDiscovered(),
                progress.symbolicLinksDiscovered(),
                progress.otherEntriesDiscovered(),
                progress.entriesSkipped(),
                progress.failures(),
                indexedEntries);
    }

    private static ScanJobMetrics metrics(MetadataIndexingOutcome outcome) {
        var discovery = outcome.discovery();
        return new ScanJobMetrics(
                discovery.entriesDiscovered(),
                discovery.filesDiscovered(),
                discovery.directoriesDiscovered(),
                discovery.symbolicLinksDiscovered(),
                discovery.otherEntriesDiscovered(),
                discovery.entriesSkipped(),
                discovery.failures(),
                outcome.entriesIndexed());
    }

    private static ScanJobMetrics metrics(IndexingJobStatus status) {
        return new ScanJobMetrics(
                status.entriesDiscovered(),
                status.filesDiscovered(),
                status.directoriesDiscovered(),
                status.symbolicLinksDiscovered(),
                status.otherEntriesDiscovered(),
                status.entriesSkipped(),
                status.failures(),
                status.entriesIndexed());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
