package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.config.DeepFindWatcherProperties;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import com.deepfind.extraction.ParsedDocument;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.filesystem.watch.RecursiveFileWatcher;
import com.deepfind.persistence.RootCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexWatchCoordinatorTests {

    private static final Duration CHANGE_TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresThePersistedRootAndKeepsItsIndexFresh() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        try (Fixture fixture = fixture(root)) {
            fixture.coordinator.restorePersistedRoot();
            assertThat(fixture.coordinator.status().state()).isEqualTo(IndexWatchState.WATCHING);

            Path file = Files.writeString(root.resolve("live.txt"), "first watcher term cedar");
            assertEventually(() -> fixture.index.search("cedar", 10).size() == 1);

            Files.writeString(file, "replacement watcher term juniper");
            assertEventually(() -> fixture.index.search("cedar", 10).isEmpty()
                    && fixture.index.search("juniper", 10).size() == 1);

            Files.delete(file);
            assertEventually(() -> fixture.index.search("live.txt", 10).isEmpty());
            assertThat(fixture.coordinator.status().state()).isEqualTo(IndexWatchState.WATCHING);
        }
    }

    @Test
    void switchesRootsAndStopsThePreviousWatcher() throws Exception {
        Path firstRoot = Files.createDirectories(temporaryDirectory.resolve("first"));
        Path secondRoot = Files.createDirectories(temporaryDirectory.resolve("second"));
        try (Fixture fixture = fixture(firstRoot)) {
            fixture.coordinator.restorePersistedRoot();
            fixture.coordinator.watch(secondRoot);

            Files.writeString(firstRoot.resolve("ignored-after-switch.txt"), "old root signal");
            Files.writeString(secondRoot.resolve("active-after-switch.txt"), "new root signal");

            assertEventually(
                    () -> fixture.index.search("active-after-switch.txt", 10).size() == 1);
            assertThat(fixture.index.search("ignored-after-switch.txt", 10)).isEmpty();
            assertThat(fixture.coordinator.status().root())
                    .isEqualTo(secondRoot.toAbsolutePath().normalize());
        }
    }

    @Test
    void reportsAnUnavailablePersistedRootWithoutFailingStartup() {
        Path missingRoot = temporaryDirectory.resolve("missing");
        try (Fixture fixture = fixture(missingRoot)) {
            fixture.coordinator.restorePersistedRoot();

            assertThat(fixture.coordinator.status().state()).isEqualTo(IndexWatchState.FAILED);
            assertThat(fixture.coordinator.status().message()).doesNotContain(missingRoot.toString());
        }
    }

    private Fixture fixture(Path restoredRoot) {
        LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"));
        ContentExtractor extractor = path -> {
            try {
                return ExtractionResult.success(new ParsedDocument(Files.readString(path), "text/plain", false));
            } catch (IOException exception) {
                return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, "", "READ_FAILED");
            }
        };
        DeepFindExtractionProperties extractionProperties =
                new DeepFindExtractionProperties(1_000_000, 100_000, Duration.ofSeconds(5), 1, 4);
        MetadataIndexingService fullIndexer =
                new MetadataIndexingService(new FileSystemDiscoveryService(), index, extractor, extractionProperties);
        IncrementalIndexingService incremental = new IncrementalIndexingService(
                index, fullIndexer, extractor, new DeepFindWatcherProperties(16, Duration.ofSeconds(5)));
        IndexWatchCoordinator coordinator =
                new IndexWatchCoordinator(new StubRootCatalog(restoredRoot), new RecursiveFileWatcher(), incremental);
        return new Fixture(index, coordinator);
    }

    private static void assertEventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + CHANGE_TIMEOUT.toNanos();
        boolean matched = condition.getAsBoolean();
        while (!matched && System.nanoTime() < deadline) {
            Thread.sleep(20);
            matched = condition.getAsBoolean();
        }
        assertThat(matched).isTrue();
    }

    private record Fixture(LuceneMetadataIndex index, IndexWatchCoordinator coordinator) implements AutoCloseable {

        @Override
        public void close() {
            coordinator.shutdown();
            index.close();
        }
    }

    private static final class StubRootCatalog implements RootCatalog {

        private final Path restoredRoot;

        private StubRootCatalog(Path restoredRoot) {
            this.restoredRoot = restoredRoot;
        }

        @Override
        public Optional<Path> lastSelectedRoot() {
            return Optional.of(restoredRoot);
        }

        @Override
        public void rememberSelected(Path root, Instant selectedAt) {}

        @Override
        public void markIndexed(Path root, Instant indexedAt) {}
    }
}
