package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.config.DeepFindExtractionProperties;
import com.deepfind.extraction.ContentExtractor;
import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import com.deepfind.extraction.ParsedDocument;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataReconciliationServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void repairsNewChangedAndDeletedEntriesWithoutReextractingUnchangedFiles() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path unchanged = Files.writeString(root.resolve("unchanged.txt"), "stable amber content");
        Path changed = Files.writeString(root.resolve("changed.txt"), "obsolete cedar content");
        Path removed = Files.writeString(root.resolve("removed.txt"), "removed cobalt content");
        Path siblingRoot = Files.createDirectories(temporaryDirectory.resolve("root-copy"));
        Path sibling = Files.writeString(siblingRoot.resolve("sibling.txt"), "sibling saffron content");
        RecordingExtractor extractor = new RecordingExtractor();

        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"))) {
            MetadataIndexingService fullIndexer = fullIndexer(index, extractor);
            fullIndexer.indexRoot(root, ExclusionPolicy.none(), new DiscoveryObserver() {});
            fullIndexer.indexRoot(siblingRoot, ExclusionPolicy.none(), new DiscoveryObserver() {});
            extractor.paths.clear();

            Files.writeString(changed, "replacement juniper content");
            Files.delete(removed);
            Path added = Files.writeString(root.resolve("added.txt"), "new mahogany content");

            MetadataIndexingOutcome outcome = reconciliation(index, extractor)
                    .reconcileRoot(root, ExclusionPolicy.none(), new DiscoveryObserver() {});

            assertThat(extractor.paths)
                    .containsExactlyInAnyOrder(changed, added)
                    .doesNotContain(unchanged);
            assertThat(index.search("cedar", 10)).isEmpty();
            assertThat(index.search("juniper", 10)).singleElement();
            assertThat(index.search("removed.txt", 10)).isEmpty();
            assertThat(index.search("saffron", 10)).singleElement().satisfies(result -> assertThat(
                            result.metadata().absolutePath())
                    .isEqualTo(sibling.toAbsolutePath().normalize()));
            assertThat(outcome.entriesIndexed()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void retriesContentThatWasNeverAttemptedBeforeAnInterruption() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path file = Files.writeString(root.resolve("partial.txt"), "recoverable violet content");
        BasicFileAttributes attributes =
                Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        RecordingExtractor extractor = new RecordingExtractor();

        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"))) {
            index.upsert(FileMetadata.from(file, attributes));
            index.commit();

            reconciliation(index, extractor).reconcileRoot(root, ExclusionPolicy.none(), new DiscoveryObserver() {});

            assertThat(extractor.paths).containsExactly(file);
            assertThat(index.search("violet", 10)).singleElement();
        }
    }

    private MetadataReconciliationService reconciliation(LuceneMetadataIndex index, ContentExtractor extractor) {
        return new MetadataReconciliationService(
                new FileSystemDiscoveryService(), index, extractor, extractionProperties());
    }

    private MetadataIndexingService fullIndexer(LuceneMetadataIndex index, ContentExtractor extractor) {
        return new MetadataIndexingService(new FileSystemDiscoveryService(), index, extractor, extractionProperties());
    }

    private static DeepFindExtractionProperties extractionProperties() {
        return new DeepFindExtractionProperties(1_000_000, 100_000, Duration.ofSeconds(5), 1, 4);
    }

    private static final class RecordingExtractor implements ContentExtractor {

        private final List<Path> paths = new CopyOnWriteArrayList<>();

        @Override
        public ExtractionResult extract(Path path) {
            paths.add(path);
            try {
                return ExtractionResult.success(new ParsedDocument(Files.readString(path), "text/plain", false));
            } catch (IOException exception) {
                return ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, "", "READ_FAILED");
            }
        }
    }
}
