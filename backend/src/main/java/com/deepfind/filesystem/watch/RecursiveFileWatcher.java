package com.deepfind.filesystem.watch;

import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.PathNormalizer;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class RecursiveFileWatcher {

    public FileWatchSession watch(Path root, ExclusionPolicy exclusions, FileChangeObserver observer) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        Objects.requireNonNull(observer, "observer must not be null");
        validateRoot(absoluteRoot);
        return new NioFileWatchSession(absoluteRoot, exclusions, observer);
    }

    private static void validateRoot(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileWatchStartupException("Watch root does not exist.");
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new FileWatchStartupException("Watch root must be a real directory, not a file or symbolic link.");
        }
    }

    private static final class NioFileWatchSession implements FileWatchSession {

        private static final long CLOSE_JOIN_MILLIS = 2_000;

        private final Path root;
        private final ExclusionPolicy exclusions;
        private final FileChangeObserver observer;
        private final WatchService watchService;
        private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread worker;

        private NioFileWatchSession(Path root, ExclusionPolicy exclusions, FileChangeObserver observer) {
            this.root = root;
            this.exclusions = exclusions;
            this.observer = observer;
            try {
                this.watchService = root.getFileSystem().newWatchService();
                registerTree(root, true);
            } catch (IOException | SecurityException exception) {
                closeAfterStartupFailure();
                throw startupFailure(exception);
            }
            this.worker = Thread.ofPlatform()
                    .daemon(true)
                    .name("deepfind-file-watcher-" + Integer.toUnsignedString(root.hashCode()))
                    .unstarted(this::run);
            this.worker.start();
        }

        private void closeAfterStartupFailure() {
            try {
                if (watchService != null) {
                    watchService.close();
                }
            } catch (IOException ignored) {
                // Preserve the original startup failure.
            }
        }

        @Override
        public Path root() {
            return root;
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        private void run() {
            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (ClosedWatchServiceException exception) {
                    return;
                } catch (InterruptedException exception) {
                    if (!running.get()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                } catch (RuntimeException exception) {
                    reportFailure(root, FileWatchFailureReason.IO_ERROR, "Filesystem watcher stopped unexpectedly.");
                    stopFromWorker();
                    return;
                }

                Path directory = directories.get(key);
                if (directory != null) {
                    processEvents(key, directory);
                }
                if (!key.reset()) {
                    Path invalidDirectory = directories.remove(key);
                    if (root.equals(invalidDirectory)) {
                        reportFailure(root, FileWatchFailureReason.NOT_FOUND, "Watch root is no longer available.");
                        stopFromWorker();
                        return;
                    }
                }
            }
        }

        private void stopFromWorker() {
            running.set(false);
            try {
                watchService.close();
            } catch (IOException ignored) {
                // The worker is already terminating.
            }
        }

        private void processEvents(WatchKey key, Path directory) {
            for (WatchEvent<?> rawEvent : key.pollEvents()) {
                FileChangeKind kind;
                try {
                    kind = FileWatchEventMapper.map(rawEvent.kind());
                } catch (IllegalArgumentException exception) {
                    continue;
                }

                if (kind == FileChangeKind.OVERFLOW) {
                    notifyChange(new FileChangeEvent(directory, kind));
                    continue;
                }

                Path context = eventContext(rawEvent);
                if (context == null) {
                    continue;
                }
                Path changedPath = PathNormalizer.absolute(directory.resolve(context));
                if (exclusions.excludes(root, changedPath)) {
                    continue;
                }
                if (kind == FileChangeKind.CREATED && isRealDirectory(changedPath)) {
                    try {
                        registerTree(changedPath, false);
                    } catch (IOException | SecurityException exception) {
                        reportRegistrationFailure(changedPath, exception);
                    }
                }
                notifyChange(new FileChangeEvent(changedPath, kind));
            }
        }

        @SuppressWarnings("unchecked")
        private static Path eventContext(WatchEvent<?> event) {
            Object context = event.context();
            return context instanceof Path ? ((WatchEvent<Path>) event).context() : null;
        }

        private static boolean isRealDirectory(Path path) {
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
        }

        private void registerTree(Path start, boolean failOnRootError) throws IOException {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                        throws IOException {
                    if (exclusions.excludes(root, directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    try {
                        WatchKey key = directory.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                        directories.put(key, PathNormalizer.absolute(directory));
                    } catch (IOException | SecurityException exception) {
                        if (failOnRootError && directory.equals(start)) {
                            if (exception instanceof IOException ioException) {
                                throw ioException;
                            }
                            throw exception;
                        }
                        reportRegistrationFailure(directory, exception);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException {
                    if (failOnRootError && path.equals(start)) {
                        throw exception;
                    }
                    reportRegistrationFailure(path, exception);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        }

        private void notifyChange(FileChangeEvent event) {
            try {
                observer.onChange(event);
            } catch (RuntimeException exception) {
                reportFailure(
                        event.path(), FileWatchFailureReason.OBSERVER_ERROR, "File-change observer rejected an event.");
            }
        }

        private void reportRegistrationFailure(Path path, Exception exception) {
            FileWatchFailureReason reason = exception instanceof AccessDeniedException
                    ? FileWatchFailureReason.PERMISSION_DENIED
                    : exception instanceof NoSuchFileException
                            ? FileWatchFailureReason.NOT_FOUND
                            : FileWatchFailureReason.IO_ERROR;
            String message =
                    switch (reason) {
                        case PERMISSION_DENIED -> "Operating system denied watcher access.";
                        case NOT_FOUND -> "Directory disappeared before it could be watched.";
                        default -> "Directory could not be registered for change tracking.";
                    };
            reportFailure(path, reason, message);
        }

        private void reportFailure(Path path, FileWatchFailureReason reason, String message) {
            try {
                observer.onFailure(new FileWatchFailure(path, reason, message));
            } catch (RuntimeException ignored) {
                // Observer failures must not terminate the filesystem watcher.
            }
        }

        @Override
        public void close() {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            try {
                watchService.close();
            } catch (IOException ignored) {
                // Closing is best-effort and the worker is interrupted below.
            }
            worker.interrupt();
            if (Thread.currentThread() == worker) {
                return;
            }
            try {
                worker.join(CLOSE_JOIN_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private static FileWatchStartupException startupFailure(Exception exception) {
            String message = exception instanceof AccessDeniedException
                    ? "Operating system denied access to the watch root."
                    : exception instanceof NoSuchFileException
                            ? "Watch root disappeared during startup."
                            : "Filesystem change tracking could not start.";
            return new FileWatchStartupException(message, exception);
        }
    }
}
