package com.deepfind.index;

import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.PathNormalizer;
import com.deepfind.filesystem.watch.FileWatchSession;
import com.deepfind.filesystem.watch.RecursiveFileWatcher;
import com.deepfind.persistence.RootCatalog;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class IndexWatchCoordinator implements IndexWatchLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexWatchCoordinator.class);
    private static final String WATCHING_MESSAGE = "Filesystem changes are being tracked.";
    private static final String STOPPED_MESSAGE = "Filesystem change tracking is paused.";
    private static final String RECONCILIATION_MESSAGE =
            "Filesystem change tracking became uncertain. Reconciliation is required.";
    private static final String FAILED_MESSAGE = "Filesystem change tracking could not start for this folder.";

    private final RootCatalog rootCatalog;
    private final RecursiveFileWatcher watcher;
    private final IncrementalIndexingService incrementalIndexing;

    private ActiveWatch active;
    private IndexWatchStatus status = new IndexWatchStatus(null, IndexWatchState.STOPPED, STOPPED_MESSAGE);

    public IndexWatchCoordinator(
            RootCatalog rootCatalog, RecursiveFileWatcher watcher, IncrementalIndexingService incrementalIndexing) {
        this.rootCatalog = Objects.requireNonNull(rootCatalog, "rootCatalog must not be null");
        this.watcher = Objects.requireNonNull(watcher, "watcher must not be null");
        this.incrementalIndexing = Objects.requireNonNull(incrementalIndexing, "incrementalIndexing must not be null");
    }

    @PostConstruct
    public void restorePersistedRoot() {
        rootCatalog.lastSelectedRoot().ifPresent(this::watch);
    }

    @Override
    public synchronized void watch(Path requestedRoot) {
        Path root = PathNormalizer.absolute(requestedRoot);
        if (active != null
                && active.root().equals(root)
                && active.watchSession().isRunning()) {
            status = currentStatus(active);
            return;
        }

        stopActive();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)
                || !Files.isReadable(root)) {
            status = new IndexWatchStatus(root, IndexWatchState.FAILED, FAILED_MESSAGE);
            return;
        }

        IncrementalIndexingSession indexingSession = null;
        try {
            ExclusionPolicy exclusions = ExclusionPolicy.defaults();
            indexingSession = incrementalIndexing.openSession(root, exclusions);
            FileWatchSession watchSession = watcher.watch(root, exclusions, indexingSession);
            active = new ActiveWatch(root, watchSession, indexingSession);
            status = currentStatus(active);
        } catch (RuntimeException exception) {
            closeIncremental(indexingSession);
            status = new IndexWatchStatus(root, IndexWatchState.FAILED, FAILED_MESSAGE);
            LOGGER.error(
                    "Filesystem change tracking failed to start: {}.",
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized void pause() {
        Path previousRoot = active == null ? status.root() : active.root();
        stopActive();
        status = new IndexWatchStatus(previousRoot, IndexWatchState.STOPPED, STOPPED_MESSAGE);
    }

    public synchronized IndexWatchStatus status() {
        if (active != null) {
            status = currentStatus(active);
        }
        return status;
    }

    private static IndexWatchStatus currentStatus(ActiveWatch active) {
        if (!active.watchSession().isRunning() || active.indexingSession().reconciliationRequired()) {
            return new IndexWatchStatus(active.root(), IndexWatchState.RECONCILIATION_REQUIRED, RECONCILIATION_MESSAGE);
        }
        return new IndexWatchStatus(active.root(), IndexWatchState.WATCHING, WATCHING_MESSAGE);
    }

    private void stopActive() {
        if (active == null) {
            return;
        }
        ActiveWatch stopping = active;
        active = null;
        try {
            stopping.watchSession().close();
        } finally {
            closeIncremental(stopping.indexingSession());
        }
    }

    private static void closeIncremental(IncrementalIndexingSession session) {
        if (session != null) {
            session.close();
        }
    }

    @PreDestroy
    void shutdown() {
        pause();
    }

    private record ActiveWatch(Path root, FileWatchSession watchSession, IncrementalIndexingSession indexingSession) {}
}
