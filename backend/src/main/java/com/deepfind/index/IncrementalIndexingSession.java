package com.deepfind.index;

import com.deepfind.filesystem.watch.FileChangeObserver;
import java.time.Duration;

public interface IncrementalIndexingSession extends FileChangeObserver, AutoCloseable {

    boolean reconciliationRequired();

    int pendingEvents();

    boolean awaitIdle(Duration timeout);

    @Override
    void close();
}
