package com.deepfind.index;

import java.nio.file.Path;

public interface IndexWatchLifecycle {

    void pause();

    void watch(Path root);
}
