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
import com.deepfind.persistence.ScanFailureRecord;
import com.deepfind.persistence.ScanHistoryRepository;
import com.deepfind.persistence.ScanJobMetrics;
import com.deepfind.persistence.ScanJobRecord;
import com.deepfind.persistence.ScanJobState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
                    new RecordingScanHistory(null),
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
        RecordingScanHistory history = new RecordingScanHistory(null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(root.resolve("index-restoration"))) {
            MetadataIndexingService indexing = new MetadataIndexingService(
                    new FileSystemDiscoveryService(),
                    index,
                    path -> ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "TEST_METADATA_ONLY"),
                    new DeepFindExtractionProperties(1_000, 1_000, Duration.ofSeconds(1), 1, 2));
            Instant now = Instant.parse("2026-09-04T07:00:00Z");
            IndexingJobService jobs =
                    new IndexingJobService(indexing, catalog, history, Clock.fixed(now, ZoneOffset.UTC), executor);
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
                assertThat(history.startedJobId).isEqualTo(jobs.status().jobId());
                assertThat(history.finishedState).isEqualTo(ScanJobState.COMPLETED);
                assertThat(history.finishedMetrics.entriesIndexed()).isOne();
            } finally {
                jobs.shutdown();
            }
        }
    }

    @Test
    void exposesAnInterruptedJobOnTheNextStartup() throws Exception {
        Path interruptedRoot = root.resolve("interrupted").toAbsolutePath().normalize();
        UUID jobId = UUID.randomUUID();
        ScanJobRecord interrupted = new ScanJobRecord(
                jobId,
                interruptedRoot,
                ScanJobState.INTERRUPTED,
                interruptedRoot.resolve("partial.txt"),
                new ScanJobMetrics(20, 12, 7, 1, 0, 2, 1, 18),
                new ScanFailureRecord(
                        1,
                        interruptedRoot.resolve("locked"),
                        "PERMISSION_DENIED",
                        "DeepFind could not read this path.",
                        Instant.parse("2026-09-04T07:01:00Z")),
                "The previous indexing run was interrupted. Start indexing again to reconcile this folder.",
                Instant.parse("2026-09-04T07:00:00Z"),
                Instant.parse("2026-09-04T07:02:00Z"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(root.resolve("index-interrupted"))) {
            MetadataIndexingService indexing = new MetadataIndexingService(
                    new FileSystemDiscoveryService(),
                    index,
                    path -> ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "TEST_METADATA_ONLY"),
                    new DeepFindExtractionProperties(1_000, 1_000, Duration.ofSeconds(1), 1, 2));
            IndexingJobService jobs = new IndexingJobService(
                    indexing,
                    new RecordingRootCatalog(interruptedRoot),
                    new RecordingScanHistory(interrupted),
                    Clock.fixed(Instant.parse("2026-09-04T07:02:00Z"), ZoneOffset.UTC),
                    executor);
            try {
                assertThat(jobs.status().jobId()).isEqualTo(jobId);
                assertThat(jobs.status().state()).isEqualTo(IndexingJobState.INTERRUPTED);
                assertThat(jobs.status().entriesDiscovered()).isEqualTo(20);
                assertThat(jobs.status().lastFailure().reason().name()).isEqualTo("PERMISSION_DENIED");
                assertThat(jobs.status().errorMessage()).contains("Start indexing again");
            } finally {
                jobs.shutdown();
            }
        }
    }

    @Test
    void persistsATerminalFailureWhenIndexingStopsUnexpectedly() throws Exception {
        RecordingScanHistory history = new RecordingScanHistory(null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LuceneMetadataIndex index = new LuceneMetadataIndex(root.resolve("index-failure"))) {
            MetadataIndexingService indexing = new MetadataIndexingService(
                    new FailingDiscoveryService(),
                    index,
                    path -> ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "TEST_METADATA_ONLY"),
                    new DeepFindExtractionProperties(1_000, 1_000, Duration.ofSeconds(1), 1, 2));
            IndexingJobService jobs = new IndexingJobService(
                    indexing,
                    new RecordingRootCatalog(null),
                    history,
                    Clock.fixed(Instant.parse("2026-09-05T02:00:00Z"), ZoneOffset.UTC),
                    executor);
            try {
                jobs.start(root);
                awaitTerminal(jobs, Duration.ofSeconds(2));

                assertThat(jobs.status().state()).isEqualTo(IndexingJobState.FAILED);
                assertThat(history.finishedState).isEqualTo(ScanJobState.FAILED);
                assertThat(history.finishedMetrics).isEqualTo(ScanJobMetrics.empty());
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

    private static final class FailingDiscoveryService extends FileSystemDiscoveryService {

        @Override
        public DiscoverySummary discover(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
            throw new IllegalStateException("simulated indexing failure");
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

    private static final class RecordingScanHistory implements ScanHistoryRepository {

        private final ScanJobRecord interrupted;
        private volatile UUID startedJobId;
        private volatile ScanJobState finishedState;
        private volatile ScanJobMetrics finishedMetrics;

        private RecordingScanHistory(ScanJobRecord interrupted) {
            this.interrupted = interrupted;
        }

        @Override
        public Optional<ScanJobRecord> interruptRunningJobs(Instant interruptedAt, String message) {
            return Optional.ofNullable(interrupted);
        }

        @Override
        public void start(UUID jobId, Path root, Instant startedAt) {
            this.startedJobId = jobId;
        }

        @Override
        public void checkpoint(UUID jobId, Path currentPath, ScanJobMetrics metrics) {}

        @Override
        public void recordFailure(UUID jobId, Path path, String reason, String message, Instant recordedAt) {}

        @Override
        public void finish(
                UUID jobId, ScanJobState state, ScanJobMetrics metrics, String errorMessage, Instant finishedAt) {
            this.finishedState = state;
            this.finishedMetrics = metrics;
        }

        @Override
        public List<ScanJobRecord> findRecent(int limit) {
            return interrupted == null ? List.of() : List.of(interrupted);
        }
    }
}
