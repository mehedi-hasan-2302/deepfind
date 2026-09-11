package com.deepfind.index;

import static com.deepfind.diagnostics.PrivacySafeDiagnostics.logExtractionOutcome;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.filesystem.DiscoveryFailure;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoveryProgress;
import com.deepfind.filesystem.DiscoverySummary;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.filesystem.FileSystemEntryKind;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class MetadataIndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIndexingService.class);

    private final FileSystemDiscoveryService discoveryService;
    private final LuceneMetadataIndex index;
    private final ContentExtractor contentExtractor;
    private final int extractionWorkerCount;
    private final int extractionQueueCapacity;

    public MetadataIndexingService(
            FileSystemDiscoveryService discoveryService,
            LuceneMetadataIndex index,
            ContentExtractor contentExtractor,
            DeepFindExtractionProperties extractionProperties) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService must not be null");
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
        Objects.requireNonNull(extractionProperties, "extractionProperties must not be null");
        extractionWorkerCount = extractionProperties.workerCount();
        extractionQueueCapacity = extractionProperties.queueCapacity();
    }

    public MetadataIndexingOutcome indexRoot(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        AtomicLong indexed = new AtomicLong();
        AtomicReference<RuntimeException> extractionFailure = new AtomicReference<>();
        ThreadPoolExecutor extractionWorkers = extractionWorkers();
        DiscoverySummary summary = null;
        IndexingPausedException paused = null;
        try {
            summary = discoveryService.discover(root, exclusions, new DiscoveryObserver() {
                @Override
                public void onEntry(FileMetadata metadata) {
                    index.upsert(metadata);
                    indexed.incrementAndGet();
                    observer.onEntry(metadata);
                    if (metadata.kind() == FileSystemEntryKind.FILE) {
                        extractionWorkers.execute(() -> extractAndUpdate(metadata, extractionFailure));
                    }
                }

                @Override
                public void onFailure(DiscoveryFailure failure) {
                    observer.onFailure(failure);
                }

                @Override
                public void onProgress(DiscoveryProgress progress) {
                    observer.onProgress(progress);
                }
            });
        } catch (IndexingPausedException exception) {
            paused = exception;
        } finally {
            finishExtraction(extractionWorkers);
        }
        RuntimeException failure = extractionFailure.get();
        if (failure != null) {
            throw failure;
        }
        if (paused != null) {
            index.commit();
            throw paused;
        }
        index.commit();
        return new MetadataIndexingOutcome(summary, indexed.get());
    }

    private void extractAndUpdate(FileMetadata metadata, AtomicReference<RuntimeException> failure) {
        if (failure.get() != null) {
            return;
        }
        try {
            ExtractionResult extraction = contentExtractor.extract(metadata.absolutePath());
            logExtractionOutcome(LOGGER, extraction);
            index.upsertContent(metadata, extraction);
        } catch (RuntimeException exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private ThreadPoolExecutor extractionWorkers() {
        return new ThreadPoolExecutor(
                extractionWorkerCount,
                extractionWorkerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(extractionQueueCapacity),
                extractionThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static void finishExtraction(ThreadPoolExecutor workers) {
        workers.shutdown();
        try {
            while (!workers.awaitTermination(1, TimeUnit.MINUTES)) {
                // Keep waiting in bounded intervals so interruption can cancel the pipeline.
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Content extraction was interrupted.", exception);
        }
    }

    private static ThreadFactory extractionThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "deepfind-content-indexer-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
