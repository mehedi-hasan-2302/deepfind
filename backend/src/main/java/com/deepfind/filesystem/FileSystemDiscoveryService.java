package com.deepfind.filesystem;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FileSystemDiscoveryService {

    public DiscoverySummary discover(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        Objects.requireNonNull(observer, "observer must not be null");

        MutableDiscoveryState state = new MutableDiscoveryState(absoluteRoot, observer);
        if (!Files.exists(absoluteRoot, LinkOption.NOFOLLOW_LINKS)) {
            state.recordFailure(absoluteRoot, DiscoveryFailureReason.NOT_FOUND, "Indexed root does not exist.");
            return state.summary();
        }
        if (!Files.isDirectory(absoluteRoot, LinkOption.NOFOLLOW_LINKS)) {
            state.recordFailure(absoluteRoot, DiscoveryFailureReason.NOT_DIRECTORY, "Indexed root is not a directory.");
            return state.summary();
        }

        try {
            Files.walkFileTree(absoluteRoot, new DiscoveryFileVisitor(absoluteRoot, exclusions, state));
        } catch (AccessDeniedException exception) {
            state.recordFailure(
                    exceptionPath(exception, absoluteRoot),
                    DiscoveryFailureReason.PERMISSION_DENIED,
                    "Operating system denied access.");
        } catch (NoSuchFileException exception) {
            state.recordFailure(
                    exceptionPath(exception, absoluteRoot),
                    DiscoveryFailureReason.NOT_FOUND,
                    "Entry disappeared during discovery.");
        } catch (SecurityException exception) {
            state.recordFailure(absoluteRoot, DiscoveryFailureReason.SECURITY_ERROR, "Security policy denied access.");
        } catch (IOException exception) {
            state.recordFailure(
                    exceptionPath(exception, absoluteRoot),
                    DiscoveryFailureReason.IO_ERROR,
                    "Filesystem discovery could not continue.");
        }
        return state.summary();
    }

    private static Path exceptionPath(IOException exception, Path fallback) {
        if (exception instanceof FileSystemException fileSystemException && fileSystemException.getFile() != null) {
            return Path.of(fileSystemException.getFile()).toAbsolutePath().normalize();
        }
        return fallback;
    }

    private static final class DiscoveryFileVisitor implements FileVisitor<Path> {

        private final Path root;
        private final ExclusionPolicy exclusions;
        private final MutableDiscoveryState state;

        private DiscoveryFileVisitor(Path root, ExclusionPolicy exclusions, MutableDiscoveryState state) {
            this.root = root;
            this.exclusions = exclusions;
            this.state = state;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (exclusions.excludes(root, directory)) {
                state.recordSkipped(directory);
                return FileVisitResult.SKIP_SUBTREE;
            }
            state.recordEntry(directory, attributes);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (exclusions.excludes(root, file)) {
                state.recordSkipped(file);
            } else {
                state.recordEntry(file, attributes);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            DiscoveryFailureReason reason = exception instanceof AccessDeniedException
                    ? DiscoveryFailureReason.PERMISSION_DENIED
                    : exception instanceof NoSuchFileException
                            ? DiscoveryFailureReason.NOT_FOUND
                            : DiscoveryFailureReason.IO_ERROR;
            String message =
                    switch (reason) {
                        case PERMISSION_DENIED -> "Operating system denied access.";
                        case NOT_FOUND -> "Entry disappeared during discovery.";
                        default -> "Entry could not be read.";
                    };
            state.recordFailure(file, reason, message);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
            if (exception != null) {
                state.recordFailure(directory, DiscoveryFailureReason.IO_ERROR, "Directory could not be fully read.");
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class MutableDiscoveryState {

        private final Path root;
        private final DiscoveryObserver observer;
        private long entriesDiscovered;
        private long filesDiscovered;
        private long directoriesDiscovered;
        private long symbolicLinksDiscovered;
        private long otherEntriesDiscovered;
        private long entriesSkipped;
        private long failures;

        private MutableDiscoveryState(Path root, DiscoveryObserver observer) {
            this.root = root;
            this.observer = observer;
        }

        private void recordEntry(Path path, BasicFileAttributes attributes) {
            FileMetadata metadata = FileMetadata.from(path, attributes);
            FileSystemEntryKind kind = metadata.kind();
            entriesDiscovered++;
            switch (kind) {
                case FILE -> filesDiscovered++;
                case DIRECTORY -> directoriesDiscovered++;
                case SYMBOLIC_LINK -> symbolicLinksDiscovered++;
                case OTHER -> otherEntriesDiscovered++;
            }
            observer.onEntry(metadata);
            notifyProgress(metadata.absolutePath());
        }

        private void recordSkipped(Path path) {
            entriesSkipped++;
            notifyProgress(path);
        }

        private void recordFailure(Path path, DiscoveryFailureReason reason, String message) {
            Path absolutePath = PathNormalizer.absolute(path);
            failures++;
            observer.onFailure(new DiscoveryFailure(absolutePath, reason, message));
            notifyProgress(absolutePath);
        }

        private void notifyProgress(Path currentPath) {
            observer.onProgress(new DiscoveryProgress(
                    PathNormalizer.absolute(currentPath),
                    entriesDiscovered,
                    filesDiscovered,
                    directoriesDiscovered,
                    symbolicLinksDiscovered,
                    otherEntriesDiscovered,
                    entriesSkipped,
                    failures));
        }

        private DiscoverySummary summary() {
            return new DiscoverySummary(
                    root,
                    entriesDiscovered,
                    filesDiscovered,
                    directoriesDiscovered,
                    symbolicLinksDiscovered,
                    otherEntriesDiscovered,
                    entriesSkipped,
                    failures);
        }
    }
}
