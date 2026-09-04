package com.deepfind.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataIndexingServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesStreamedDiscoveryEntriesWithoutDuplicates() throws IOException {
        Path root = Files.createDirectories(temporaryDirectory.resolve("corpus/Projects/DeepFind"));
        Files.writeString(root.resolve("README.md"), "metadata search");
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("target/ignored.txt"), "ignored");

        try (LuceneMetadataIndex index = new LuceneMetadataIndex(temporaryDirectory.resolve("index"))) {
            MetadataIndexingService service = new MetadataIndexingService(new FileSystemDiscoveryService(), index);

            MetadataIndexingOutcome first = service.indexRoot(
                    temporaryDirectory.resolve("corpus"), ExclusionPolicy.defaults(), new DiscoveryObserver() {});
            MetadataIndexingOutcome second = service.indexRoot(
                    temporaryDirectory.resolve("corpus"), ExclusionPolicy.defaults(), new DiscoveryObserver() {});

            assertThat(first.entriesIndexed()).isEqualTo(4);
            assertThat(first.discovery().entriesSkipped()).isEqualTo(1);
            assertThat(second.entriesIndexed()).isEqualTo(4);
            assertThat(index.count()).isEqualTo(4);
            assertThat(index.search("Projects DeepFind README", 10))
                    .singleElement()
                    .extracting(result -> result.metadata().filename())
                    .isEqualTo("README.md");
            assertThat(index.search("ignored", 10)).isEmpty();
        }
    }
}
