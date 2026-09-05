package com.deepfind.jobs;

import com.deepfind.filesystem.DiscoveryFailure;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoveryProgress;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.index.MetadataIndexingOutcome;
import com.deepfind.index.MetadataIndexingService;
import com.deepfind.persistence.RootCatalog;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IndexingJobService {

    private static final String FAILED_MESSAGE =
            "Indexing stopped because the local search index could not be updated.";

    private final MetadataIndexingService indexingService;
    private final RootCatalog rootCatalog;
    private final Clock clock;
    private final ExecutorService executor;
    private final AtomicReference<IndexingJobStatus> status;

    @Autowired
    public IndexingJobService(MetadataIndexingService indexingService, RootCatalog rootCatalog) {
        this(indexingService, rootCatalog, Clock.systemUTC(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "deepfind-indexer");
            thread.setDaemon(true);
            return thread;
        }));
    }

    IndexingJobService(
            MetadataIndexingService indexingService, RootCatalog rootCatalog, Clock clock, ExecutorService executor) {
        this.indexingService = Objects.requireNonNull(indexingService, "indexingService must not be null");
        this.rootCatalog = Objects.requireNonNull(rootCatalog, "rootCatalog must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.status = new AtomicReference<>(
                IndexingJobStatus.idle(rootCatalog.lastSelectedRoot().orElse(null)));
    }

    public synchronized IndexingJobStatus start(Path requestedRoot) {
        IndexingJobStatus current = status.get();
        if (current.state() == IndexingJobState.RUNNING) {
            throw new IndexingAlreadyRunningException();
        }

        Path root = PathNormalizer.absolute(requestedRoot);
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IndexRootNotAccessibleException("DeepFind cannot read this folder.");
        }

        var startedAt = clock.instant();
        rootCatalog.rememberSelected(root, startedAt);
        IndexingJobStatus started = IndexingJobStatus.running(UUID.randomUUID(), root, startedAt);
        status.set(started);
        executor.submit(() -> run(started));
        return started;
    }

    public IndexingJobStatus status() {
        return status.get();
    }

    private void run(IndexingJobStatus started) {
        AtomicLong indexed = new AtomicLong();
        try {
            MetadataIndexingOutcome outcome =
                    indexingService.indexRoot(started.root(), ExclusionPolicy.defaults(), new DiscoveryObserver() {
                        @Override
                        public void onEntry(FileMetadata metadata) {
                            indexed.incrementAndGet();
                        }

                        @Override
                        public void onFailure(DiscoveryFailure failure) {
                            status.updateAndGet(current -> current.withFailure(failure));
                        }

                        @Override
                        public void onProgress(DiscoveryProgress progress) {
                            status.updateAndGet(current -> current.withProgress(progress, indexed.get()));
                        }
                    });
            var finishedAt = clock.instant();
            rootCatalog.markIndexed(started.root(), finishedAt);
            status.updateAndGet(current -> current.completed(outcome, finishedAt));
        } catch (RuntimeException exception) {
            status.updateAndGet(current -> current.failed(FAILED_MESSAGE, clock.instant()));
        }
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
