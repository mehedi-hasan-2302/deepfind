package com.deepfind.filesystem.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.PathNormalizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecursiveFileWatcherTests {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);

    private final RecursiveFileWatcher watcher = new RecursiveFileWatcher();

    @TempDir
    Path temporaryDirectory;

    @Test
    void observesCreateModifyAndDeleteInAnExistingNestedDirectory() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("Documents"));
        Path file = nested.resolve("résumé.txt");
        RecordingObserver observer = new RecordingObserver();

        try (FileWatchSession session = watcher.watch(root, ExclusionPolicy.none(), observer)) {
            Files.writeString(file, "first");
            assertThat(observer.await(FileChangeKind.CREATED, file)).isNotNull();

            Files.writeString(file, "second");
            assertThat(observer.await(FileChangeKind.MODIFIED, file)).isNotNull();

            Files.delete(file);
            assertThat(observer.await(FileChangeKind.DELETED, file)).isNotNull();
            assertThat(session.root()).isEqualTo(PathNormalizer.absolute(root));
            assertThat(session.isRunning()).isTrue();
        }

        assertThat(observer.failures).isEmpty();
    }

    @Test
    void registersDirectoriesCreatedAfterWatchingStarts() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path createdDirectory = root.resolve("new-folder");
        Path nestedFile = createdDirectory.resolve("inside.md");
        RecordingObserver observer = new RecordingObserver();

        try (FileWatchSession ignored = watcher.watch(root, ExclusionPolicy.none(), observer)) {
            Files.createDirectory(createdDirectory);
            assertThat(observer.await(FileChangeKind.CREATED, createdDirectory)).isNotNull();

            Files.writeString(nestedFile, "watched");
            assertThat(observer.await(FileChangeKind.CREATED, nestedFile)).isNotNull();
        }
    }

    @Test
    void skipsExcludedTrees() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path excluded = Files.createDirectories(root.resolve("node_modules"));
        RecordingObserver observer = new RecordingObserver();

        try (FileWatchSession ignored = watcher.watch(root, ExclusionPolicy.defaults(), observer)) {
            Files.writeString(excluded.resolve("ignored.js"), "ignored");
            assertThat(observer.poll(Duration.ofMillis(500))).isNull();
        }
    }

    @Test
    void doesNotFollowDirectorySymbolicLinks() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path target = Files.createDirectories(temporaryDirectory.resolve("outside"));
        Path link = root.resolve("linked-directory");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Host does not permit symbolic-link creation: "
                    + exception.getClass().getSimpleName());
        }
        RecordingObserver observer = new RecordingObserver();

        try (FileWatchSession ignored = watcher.watch(root, ExclusionPolicy.none(), observer)) {
            Files.writeString(target.resolve("outside.txt"), "outside");
            assertThat(observer.poll(Duration.ofMillis(500))).isNull();
        }
    }

    @Test
    void rejectsMissingFileAndSymbolicLinkRoots() throws IOException {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        Path file = Files.writeString(root.resolve("single.txt"), "content");

        assertThatThrownBy(
                        () -> watcher.watch(root.resolve("missing"), ExclusionPolicy.none(), new RecordingObserver()))
                .isInstanceOf(FileWatchStartupException.class)
                .hasMessage("Watch root does not exist.");
        assertThatThrownBy(() -> watcher.watch(file, ExclusionPolicy.none(), new RecordingObserver()))
                .isInstanceOf(FileWatchStartupException.class)
                .hasMessageContaining("real directory");
    }

    @Test
    void closeIsIdempotentAndStopsTheSession() throws IOException {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        FileWatchSession session = watcher.watch(root, ExclusionPolicy.none(), new RecordingObserver());

        session.close();
        session.close();

        assertThat(session.isRunning()).isFalse();
    }

    @Test
    void reportsAndStopsWhenTheRootDisappears() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("root"));
        RecordingObserver observer = new RecordingObserver();

        try (FileWatchSession session = watcher.watch(root, ExclusionPolicy.none(), observer)) {
            Files.delete(root);

            assertThat(observer.awaitFailure(FileWatchFailureReason.NOT_FOUND, root))
                    .isNotNull();
            assertThat(session.isRunning()).isFalse();
        }
    }

    private static final class RecordingObserver implements FileChangeObserver {

        private final BlockingQueue<FileChangeEvent> changes = new LinkedBlockingQueue<>();
        private final BlockingQueue<FileWatchFailure> failures = new LinkedBlockingQueue<>();

        @Override
        public void onChange(FileChangeEvent event) {
            changes.add(event);
        }

        @Override
        public void onFailure(FileWatchFailure failure) {
            failures.add(failure);
        }

        private FileChangeEvent await(FileChangeKind kind, Path path) throws InterruptedException {
            Path absolutePath = PathNormalizer.absolute(path);
            long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                Duration remaining = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
                FileChangeEvent event = changes.poll(remaining.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event == null) {
                    return null;
                }
                if (event.kind() == kind && event.path().equals(absolutePath)) {
                    return event;
                }
            }
            return null;
        }

        private FileChangeEvent poll(Duration timeout) throws InterruptedException {
            return changes.poll(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        private FileWatchFailure awaitFailure(FileWatchFailureReason reason, Path path) throws InterruptedException {
            Path absolutePath = PathNormalizer.absolute(path);
            long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                Duration remaining = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
                FileWatchFailure failure =
                        failures.poll(remaining.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (failure == null) {
                    return null;
                }
                if (failure.reason() == reason && failure.path().equals(absolutePath)) {
                    return failure;
                }
            }
            return null;
        }
    }
}
