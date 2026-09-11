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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class MetadataReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataReconciliationService.class);

    private final FileSystemDiscoveryService discoveryService;
    private final LuceneMetadataIndex index;
    private final ContentExtractor contentExtractor;
    private final DeepFindExtractionProperties extractionProperties;

    public MetadataReconciliationService(
            FileSystemDiscoveryService discoveryService,
            LuceneMetadataIndex index,
            ContentExtractor contentExtractor,
            DeepFindExtractionProperties extractionProperties) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService must not be null");
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
        this.extractionProperties =
                Objects.requireNonNull(extractionProperties, "extractionProperties must not be null");
    }

    public MetadataIndexingOutcome reconcileRoot(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        Objects.requireNonNull(observer, "observer must not be null");
        AtomicLong changed = new AtomicLong();
        AtomicReference<RuntimeException> extractionFailure = new AtomicReference<>();
        ThreadPoolExecutor workers = extractionWorkers();
        DiscoverySummary summary = null;
        IndexingPausedException paused = null;
        try (MetadataIndexSnapshot snapshot = index.openMetadataSnapshot()) {
            try {
                summary = discoveryService.discover(root, exclusions, new DiscoveryObserver() {
                    @Override
                    public void onEntry(FileMetadata metadata) {
                        boolean unchanged = snapshot.find(metadata.absolutePath())
                                .filter(existing -> sameFilesystemState(existing.metadata(), metadata))
                                .filter(existing ->
                                        metadata.kind() != FileSystemEntryKind.FILE || existing.contentAttempted())
                                .isPresent();
                        if (!unchanged) {
                            index.upsert(metadata);
                            changed.incrementAndGet();
                            if (metadata.kind() == FileSystemEntryKind.FILE) {
                                workers.execute(() -> extractAndUpdate(metadata, extractionFailure));
                            }
                        }
                        observer.onEntry(metadata);
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
            }
        } finally {
            finishExtraction(workers);
        }
        RuntimeException failure = extractionFailure.get();
        if (failure != null) {
            throw failure;
        }
        if (paused != null) {
            index.commit();
            throw paused;
        }
        long removed = index.deleteProvenMissingUnderRoot(root, exclusions);
        index.commit();
        return new MetadataIndexingOutcome(summary, changed.get() + removed);
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
                extractionProperties.workerCount(),
                extractionProperties.workerCount(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(extractionProperties.queueCapacity()),
                threadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static boolean sameFilesystemState(FileMetadata first, FileMetadata second) {
        return first.kind() == second.kind()
                && first.sizeBytes() == second.sizeBytes()
                && first.modifiedAt().toEpochMilli() == second.modifiedAt().toEpochMilli()
                && first.createdAt().toEpochMilli() == second.createdAt().toEpochMilli();
    }

    private static void finishExtraction(ThreadPoolExecutor workers) {
        workers.shutdown();
        try {
            while (!workers.awaitTermination(1, TimeUnit.MINUTES)) {
                // Continue in bounded waits so interruption remains observable.
            }
        } catch (InterruptedException exception) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reconciliation content extraction was interrupted.", exception);
        }
    }

    private static ThreadFactory threadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "deepfind-reconciliation-extractor");
            thread.setDaemon(true);
            return thread;
        };
    }
}
