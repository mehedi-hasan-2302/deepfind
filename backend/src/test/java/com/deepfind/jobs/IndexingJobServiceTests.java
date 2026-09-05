package com.deepfind.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoverySummary;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.index.LuceneMetadataIndex;
import com.deepfind.index.MetadataIndexingService;
import com.deepfind.persistence.RootCatalog;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexingJobServiceTests {

    @TempDir
    Path root;

    @Test
    void rejectsASecondJobWhileTheWriterIsBusy() throws Exception {
        BlockingDiscoveryService discovery = new BlockingDiscoveryService();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(root.resolve("index"))) {
            MetadataIndexingService indexing = new MetadataIndexingService(
                    discovery,
                    index,
                    path -> ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "TEST_METADATA_ONLY"),
                    new DeepFindExtractionProperties(1_000, 1_000, Duration.ofSeconds(1), 1, 2));
            IndexingJobService jobs = new IndexingJobService(
                    indexing,
                    new RecordingRootCatalog(null),
                    Clock.fixed(Instant.parse("2026-09-04T06:00:00Z"), ZoneOffset.UTC),
                    executor);
            try {
                assertThat(jobs.start(root).state()).isEqualTo(IndexingJobState.RUNNING);
                assertThat(discovery.awaitStarted(Duration.ofSeconds(2))).isTrue();

                assertThatThrownBy(() -> jobs.start(root))
                        .isInstanceOf(IndexingAlreadyRunningException.class)
                        .hasMessage("An indexing job is already running.");

                discovery.release();
                awaitTerminal(jobs, Duration.ofSeconds(2));
                assertThat(jobs.status().state()).isEqualTo(IndexingJobState.COMPLETED);
            } finally {
                discovery.release();
                jobs.shutdown();
            }
        }
    }

    @Test
    void restoresTheLastSelectedRootAndRecordsSuccessfulCompletion() throws Exception {
        Path restoredRoot = root.resolve("previous").toAbsolutePath().normalize();
        Path nextRoot = java.nio.file.Files.createDirectory(root.resolve("next"));
        RecordingRootCatalog catalog = new RecordingRootCatalog(restoredRoot);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(root.resolve("index-restoration"))) {
            MetadataIndexingService indexing = new MetadataIndexingService(
                    new FileSystemDiscoveryService(),
                    index,
                    path -> ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "TEST_METADATA_ONLY"),
                    new DeepFindExtractionProperties(1_000, 1_000, Duration.ofSeconds(1), 1, 2));
            Instant now = Instant.parse("2026-09-04T07:00:00Z");
            IndexingJobService jobs =
                    new IndexingJobService(indexing, catalog, Clock.fixed(now, ZoneOffset.UTC), executor);
            try {
                assertThat(jobs.status().state()).isEqualTo(IndexingJobState.IDLE);
                assertThat(jobs.status().root()).isEqualTo(restoredRoot);

                jobs.start(nextRoot);
                awaitTerminal(jobs, Duration.ofSeconds(2));

                assertThat(jobs.status().state()).isEqualTo(IndexingJobState.COMPLETED);
                assertThat(catalog.selectedRoot)
                        .isEqualTo(nextRoot.toAbsolutePath().normalize());
                assertThat(catalog.indexedRoot)
                        .isEqualTo(nextRoot.toAbsolutePath().normalize());
                assertThat(catalog.selectedAt).isEqualTo(now);
                assertThat(catalog.indexedAt).isEqualTo(now);
            } finally {
                jobs.shutdown();
            }
        }
    }

    private static void awaitTerminal(IndexingJobService jobs, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (jobs.status().state() == IndexingJobState.RUNNING && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static final class BlockingDiscoveryService extends FileSystemDiscoveryService {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        @Override
        public DiscoverySummary discover(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
            started.countDown();
            try {
                released.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test discovery interrupted.", exception);
            }
            return new DiscoverySummary(root.toAbsolutePath().normalize(), 0, 0, 0, 0, 0, 0, 0);
        }

        boolean awaitStarted(Duration timeout) throws InterruptedException {
            return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void release() {
            released.countDown();
        }
    }

    private static final class RecordingRootCatalog implements RootCatalog {

        private final Path restoredRoot;
        private volatile Path selectedRoot;
        private volatile Path indexedRoot;
        private volatile Instant selectedAt;
        private volatile Instant indexedAt;

        private RecordingRootCatalog(Path restoredRoot) {
            this.restoredRoot = restoredRoot;
        }

        @Override
        public Optional<Path> lastSelectedRoot() {
            return Optional.ofNullable(restoredRoot);
        }

        @Override
        public void rememberSelected(Path root, Instant selectedAt) {
            this.selectedRoot = root.toAbsolutePath().normalize();
            this.selectedAt = selectedAt;
        }

        @Override
        public void markIndexed(Path root, Instant indexedAt) {
            this.indexedRoot = root.toAbsolutePath().normalize();
            this.indexedAt = indexedAt;
        }
    }
}
