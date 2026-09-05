package com.deepfind.filesystem.watch;

import java.nio.file.Path;

public interface FileWatchSession extends AutoCloseable {

    Path root();

    boolean isRunning();

    @Override
    void close();
}
