package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.config.DeepFindWatcherProperties;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import com.deepfind.extraction.ParsedDocument;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.filesystem.watch.FileChangeEvent;
import com.deepfind.filesystem.watch.FileChangeKind;
import com.deepfind.filesystem.watch.FileWatchFailure;
import com.deepfind.filesystem.watch.FileWatchFailureReason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncrementalIndexingServiceTests {

    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path temporaryDirectory;

    @Test
    void incrementallyCreatesUpdatesAndDeletesAFile() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path file = Files.writeString(root.resolve("notes.txt"), "original term cedar");

        try (Fixture fixture = fixture();
                IncrementalIndexingSession session = fixture.service.openSession(root, ExclusionPolicy.none())) {
            session.onChange(new FileChangeEvent(file, FileChangeKind.CREATED));
            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();
            assertThat(fixture.index.search("cedar", 10)).singleElement();

            Files.writeString(file, "replacement term juniper");
            session.onChange(new FileChangeEvent(file, FileChangeKind.MODIFIED));
            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();
            assertThat(fixture.index.search("cedar", 10)).isEmpty();
            assertThat(fixture.index.search("juniper", 10)).singleElement();

            Files.delete(file);
            session.onChange(new FileChangeEvent(file, FileChangeKind.DELETED));
            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();
            assertThat(fixture.index.search("notes.txt", 10)).isEmpty();
            assertThat(session.reconciliationRequired()).isFalse();
        }
    }

    @Test
    void treatsDirectoryRenameAsDeleteTreeThenCreateTree() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path originalDirectory = Files.createDirectories(root.resolve("archive"));
        Path originalFile = Files.writeString(originalDirectory.resolve("record.txt"), "cobalt record");
        Path similarlyNamedDirectory = Files.createDirectories(root.resolve("archive-copy"));
        Path similarlyNamedFile = Files.writeString(similarlyNamedDirectory.resolve("keep.txt"), "saffron record");

        try (Fixture fixture = fixture();
                IncrementalIndexingSession session = fixture.service.openSession(root, ExclusionPolicy.none())) {
            session.onChange(new FileChangeEvent(originalDirectory, FileChangeKind.CREATED));
            session.onChange(new FileChangeEvent(similarlyNamedDirectory, FileChangeKind.CREATED));
            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();

            Path renamedDirectory = root.resolve("history");
            Path renamedFile = renamedDirectory.resolve(originalFile.getFileName());
            Files.move(originalDirectory, renamedDirectory);
            session.onChange(new FileChangeEvent(originalDirectory, FileChangeKind.DELETED));
            session.onChange(new FileChangeEvent(renamedDirectory, FileChangeKind.CREATED));
            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();

            assertThat(fixture.index.search("cobalt", 10)).singleElement().satisfies(result -> assertThat(
                            result.metadata().absolutePath())
                    .isEqualTo(PathNormalizer.absolute(renamedFile)));
            assertThat(fixture.index.search("saffron", 10)).singleElement().satisfies(result -> assertThat(
                            result.metadata().absolutePath())
                    .isEqualTo(PathNormalizer.absolute(similarlyNamedFile)));
            assertThat(fixture.index.search("archive", 20))
                    .allSatisfy(result -> assertThat(result.metadata().absolutePath())
                            .satisfies(path -> assertThat(path.startsWith(PathNormalizer.absolute(originalDirectory)))
                                    .isFalse()));
        }
    }

    @Test
    void marksOverflowWatcherFailureAndOutOfRootEventsForReconciliation() throws IOException {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "outside");

        try (Fixture fixture = fixture();
                IncrementalIndexingSession session = fixture.service.openSession(root, ExclusionPolicy.none())) {
            session.onChange(new FileChangeEvent(root, FileChangeKind.OVERFLOW));
            session.onFailure(new FileWatchFailure(
                    root, FileWatchFailureReason.IO_ERROR, "Directory could not be registered for change tracking."));
            session.onChange(new FileChangeEvent(outside, FileChangeKind.CREATED));

            assertThat(session.reconciliationRequired()).isTrue();
            assertThat(session.pendingEvents()).isZero();
            assertThat(fixture.index.search("outside.txt", 10)).isEmpty();
        }
    }

    @Test
    void ignoresExcludedEventsWithoutIndexingThem() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path excluded = Files.createDirectories(root.resolve("node_modules"));
        Path file = Files.writeString(excluded.resolve("hidden.js"), "private dependency token");

        try (Fixture fixture = fixture();
                IncrementalIndexingSession session = fixture.service.openSession(root, ExclusionPolicy.defaults())) {
            session.onChange(new FileChangeEvent(file, FileChangeKind.CREATED));

            assertThat(session.awaitIdle(IDLE_TIMEOUT)).isTrue();
            assertThat(session.pendingEvents()).isZero();
            assertThat(session.reconciliationRequired()).isFalse();
            assertThat(fixture.index.search("hidden.js", 10)).isEmpty();
        }
    }

    @Test
    void closeDrainsAcceptedEventsBeforeStopping() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path file = Files.writeString(root.resolve("queued.txt"), "shutdown drain token");

        try (Fixture fixture = fixture()) {
            IncrementalIndexingSession session = fixture.service.openSession(root, ExclusionPolicy.none());
            session.onChange(new FileChangeEvent(file, FileChangeKind.CREATED));

            session.close();
            session.close();

            assertThat(session.pendingEvents()).isZero();
            assertThat(session.reconciliationRequired()).isFalse();
            assertThat(fixture.index.search("shutdown", 10)).singleElement();
        }
    }

    private Fixture fixture() {
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
        IncrementalIndexingService service = new IncrementalIndexingService(
                index, fullIndexer, extractor, new DeepFindWatcherProperties(4, Duration.ofSeconds(5)));
        return new Fixture(index, service);
    }

    private record Fixture(LuceneMetadataIndex index, IncrementalIndexingService service) implements AutoCloseable {

        @Override
        public void close() {
            index.close();
        }
    }
}
