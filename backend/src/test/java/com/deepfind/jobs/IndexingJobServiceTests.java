package com.deepfind.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoverySummary;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.index.LuceneMetadataIndex;
import com.deepfind.index.MetadataIndexingService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
            MetadataIndexingService indexing = new MetadataIndexingService(discovery, index);
            IndexingJobService jobs = new IndexingJobService(
                    indexing, Clock.fixed(Instant.parse("2026-09-04T06:00:00Z"), ZoneOffset.UTC), executor);
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
}
