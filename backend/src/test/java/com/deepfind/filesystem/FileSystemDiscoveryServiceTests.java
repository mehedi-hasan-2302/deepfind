package com.deepfind.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemDiscoveryServiceTests {

    private final FileSystemDiscoveryService service = new FileSystemDiscoveryService();

    @TempDir
    Path root;

    @Test
    void streamsMetadataAndSkipsDefaultExclusions() throws IOException {
        Path documents = Files.createDirectories(root.resolve("Documents"));
        Path unicodeFile = Files.writeString(documents.resolve("résumé.final.TXT"), "metadata only");
        Path ignoredDirectory = Files.createDirectories(root.resolve("node_modules"));
        Files.writeString(ignoredDirectory.resolve("ignored.js"), "ignored");
        RecordingObserver observer = new RecordingObserver();

        DiscoverySummary summary = service.discover(root, ExclusionPolicy.defaults(), observer);

        assertThat(observer.entries)
                .extracting(FileMetadata::absolutePath)
                .containsExactlyInAnyOrder(
                        PathNormalizer.absolute(root),
                        PathNormalizer.absolute(documents),
                        PathNormalizer.absolute(unicodeFile));
        FileMetadata fileMetadata = observer.entries.stream()
                .filter(metadata -> metadata.absolutePath().equals(PathNormalizer.absolute(unicodeFile)))
                .findFirst()
                .orElseThrow();
        assertThat(fileMetadata.filename()).isEqualTo("résumé.final.TXT");
        assertThat(fileMetadata.extension()).isEqualTo("txt");
        assertThat(fileMetadata.kind()).isEqualTo(FileSystemEntryKind.FILE);
        assertThat(summary.entriesDiscovered()).isEqualTo(3);
        assertThat(summary.filesDiscovered()).isEqualTo(1);
        assertThat(summary.directoriesDiscovered()).isEqualTo(2);
        assertThat(summary.entriesSkipped()).isEqualTo(1);
        assertThat(summary.failures()).isZero();
        assertThat(observer.progress).isNotEmpty();
        assertThat(observer.progress.getLast().entriesSkipped()).isEqualTo(1);
    }

    @Test
    void reportsAMissingRootWithoutThrowing() {
        Path missingRoot = root.resolve("missing");
        RecordingObserver observer = new RecordingObserver();

        DiscoverySummary summary = service.discover(missingRoot, ExclusionPolicy.none(), observer);

        assertThat(summary.entriesDiscovered()).isZero();
        assertThat(summary.failures()).isEqualTo(1);
        assertThat(observer.failures).singleElement().satisfies(failure -> {
            assertThat(failure.path()).isEqualTo(PathNormalizer.absolute(missingRoot));
            assertThat(failure.reason()).isEqualTo(DiscoveryFailureReason.NOT_FOUND);
            assertThat(failure.message()).doesNotContain(missingRoot.toString());
        });
    }

    @Test
    void rejectsAFileAsAnIndexedRootWithoutThrowing() throws IOException {
        Path file = Files.writeString(root.resolve("single.txt"), "content");
        RecordingObserver observer = new RecordingObserver();

        DiscoverySummary summary = service.discover(file, ExclusionPolicy.none(), observer);

        assertThat(summary.entriesDiscovered()).isZero();
        assertThat(observer.failures)
                .singleElement()
                .extracting(DiscoveryFailure::reason)
                .isEqualTo(DiscoveryFailureReason.NOT_DIRECTORY);
    }

    @Test
    void indexesASymlinkWithoutFollowingItsDirectoryTarget() throws IOException {
        Path target = Files.createDirectories(root.resolve("target-data"));
        Files.writeString(target.resolve("inside.txt"), "inside");
        Path link = root.resolve("linked-directory");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Host does not permit symbolic-link creation: "
                    + exception.getClass().getSimpleName());
        }
        RecordingObserver observer = new RecordingObserver();

        DiscoverySummary summary = service.discover(root, ExclusionPolicy.none(), observer);

        assertThat(observer.entries)
                .filteredOn(metadata -> metadata.absolutePath().equals(PathNormalizer.absolute(link)))
                .singleElement()
                .extracting(FileMetadata::kind)
                .isEqualTo(FileSystemEntryKind.SYMBOLIC_LINK);
        assertThat(observer.entries)
                .extracting(FileMetadata::absolutePath)
                .doesNotContain(PathNormalizer.absolute(link.resolve("inside.txt")));
        assertThat(summary.symbolicLinksDiscovered()).isEqualTo(1);
    }

    private static final class RecordingObserver implements DiscoveryObserver {

        private final List<FileMetadata> entries = new ArrayList<>();
        private final List<DiscoveryFailure> failures = new ArrayList<>();
        private final List<DiscoveryProgress> progress = new ArrayList<>();

        @Override
        public void onEntry(FileMetadata metadata) {
            entries.add(metadata);
        }

        @Override
        public void onFailure(DiscoveryFailure failure) {
            failures.add(failure);
        }

        @Override
        public void onProgress(DiscoveryProgress currentProgress) {
            progress.add(currentProgress);
        }
    }
}
