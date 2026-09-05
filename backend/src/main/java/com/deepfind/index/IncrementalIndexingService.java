package com.deepfind.index;

import com.deepfind.config.DeepFindWatcherProperties;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemEntryKind;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.filesystem.watch.FileChangeEvent;
import com.deepfind.filesystem.watch.FileChangeKind;
import com.deepfind.filesystem.watch.FileWatchFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public final class IncrementalIndexingService {

    private final LuceneMetadataIndex index;
    private final MetadataIndexingService fullIndexer;
    private final ContentExtractor contentExtractor;
    private final DeepFindWatcherProperties properties;

    public IncrementalIndexingService(
            LuceneMetadataIndex index,
            MetadataIndexingService fullIndexer,
            ContentExtractor contentExtractor,
            DeepFindWatcherProperties properties) {
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.fullIndexer = Objects.requireNonNull(fullIndexer, "fullIndexer must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public IncrementalIndexingSession openSession(Path root, ExclusionPolicy exclusions) {
        return new WorkerSession(PathNormalizer.absolute(root), exclusions);
    }

    private final class WorkerSession implements IncrementalIndexingSession {

        private final Path root;
        private final ExclusionPolicy exclusions;
        private final BlockingQueue<FileChangeEvent> queue;
        private final AtomicBoolean accepting = new AtomicBoolean(true);
        private final AtomicBoolean reconciliationRequired = new AtomicBoolean();
        private final AtomicInteger pendingEvents = new AtomicInteger();
        private final Object idleMonitor = new Object();
        private final Object lifecycleMonitor = new Object();
        private final Thread worker;

        private WorkerSession(Path root, ExclusionPolicy exclusions) {
            this.root = root;
            this.exclusions = Objects.requireNonNull(exclusions, "exclusions must not be null");
            this.queue = new ArrayBlockingQueue<>(properties.queueCapacity());
            this.worker = Thread.ofPlatform()
                    .daemon(true)
                    .name("deepfind-incremental-indexer-" + Integer.toUnsignedString(root.hashCode()))
                    .unstarted(this::run);
            this.worker.start();
        }

        @Override
        public void onChange(FileChangeEvent event) {
            Objects.requireNonNull(event, "event must not be null");
            if (!accepting.get()) {
                return;
            }
            if (event.kind() == FileChangeKind.OVERFLOW) {
                reconciliationRequired.set(true);
                return;
            }
            if (!event.path().startsWith(root)) {
                reconciliationRequired.set(true);
                return;
            }
            if (exclusions.excludes(root, event.path())) {
                return;
            }

            synchronized (lifecycleMonitor) {
                if (!accepting.get()) {
                    return;
                }
                pendingEvents.incrementAndGet();
                try {
                    queue.put(event);
                } catch (InterruptedException exception) {
                    eventFinished();
                    reconciliationRequired.set(true);
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void onFailure(FileWatchFailure failure) {
            Objects.requireNonNull(failure, "failure must not be null");
            if (accepting.get()) {
                reconciliationRequired.set(true);
            }
        }

        @Override
        public boolean reconciliationRequired() {
            return reconciliationRequired.get();
        }

        @Override
        public int pendingEvents() {
            return pendingEvents.get();
        }

        private void run() {
            while (accepting.get() || !queue.isEmpty()) {
                try {
                    FileChangeEvent first = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (first == null) {
                        continue;
                    }
                    List<FileChangeEvent> batch = new ArrayList<>();
                    batch.add(first);
                    queue.drainTo(batch);
                    try {
                        applyBatch(batch);
                    } catch (RuntimeException exception) {
                        reconciliationRequired.set(true);
                    } finally {
                        batch.forEach(ignored -> eventFinished());
                    }
                } catch (InterruptedException exception) {
                    if (accepting.get()) {
                        reconciliationRequired.set(true);
                    }
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void applyBatch(List<FileChangeEvent> events) {
            Map<String, FileChangeEvent> latestByPath = new LinkedHashMap<>();
            for (FileChangeEvent event : events) {
                latestByPath.merge(PathNormalizer.searchKey(event.path()), event, WorkerSession::mergeEvents);
            }
            boolean mutated = false;
            for (FileChangeEvent event : latestByPath.values()) {
                mutated |= apply(event);
            }
            if (mutated) {
                index.commit();
            }
        }

        private static FileChangeEvent mergeEvents(FileChangeEvent previous, FileChangeEvent next) {
            if (previous.kind() == FileChangeKind.CREATED && next.kind() == FileChangeKind.MODIFIED) {
                return previous;
            }
            return next;
        }

        private boolean apply(FileChangeEvent event) {
            if (event.kind() == FileChangeKind.DELETED) {
                index.deleteTree(event.path());
                return true;
            }
            if (!Files.exists(event.path(), LinkOption.NOFOLLOW_LINKS)) {
                index.deleteTree(event.path());
                return true;
            }

            FileMetadata metadata;
            try {
                BasicFileAttributes attributes =
                        Files.readAttributes(event.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                metadata = FileMetadata.from(event.path(), attributes);
            } catch (NoSuchFileException exception) {
                index.deleteTree(event.path());
                return true;
            } catch (IOException | SecurityException exception) {
                reconciliationRequired.set(true);
                return false;
            }

            if (event.kind() == FileChangeKind.CREATED && metadata.kind() == FileSystemEntryKind.DIRECTORY) {
                fullIndexer.indexRoot(event.path(), exclusions, new DiscoveryObserver() {});
                return false;
            }

            index.upsert(metadata);
            if (metadata.kind() == FileSystemEntryKind.FILE) {
                ExtractionResult extraction = contentExtractor.extract(metadata.absolutePath());
                index.upsertContent(metadata, extraction);
            }
            return true;
        }

        private void eventFinished() {
            if (pendingEvents.decrementAndGet() == 0) {
                synchronized (idleMonitor) {
                    idleMonitor.notifyAll();
                }
            }
        }

        @Override
        public boolean awaitIdle(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (idleMonitor) {
                while (pendingEvents.get() > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(idleMonitor, remaining);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public void close() {
            synchronized (lifecycleMonitor) {
                if (!accepting.compareAndSet(true, false)) {
                    return;
                }
            }
            try {
                worker.join(properties.shutdownTimeout().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (worker.isAlive()) {
                reconciliationRequired.set(true);
                worker.interrupt();
            }
        }
    }
}
