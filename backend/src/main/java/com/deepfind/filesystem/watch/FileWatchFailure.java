package com.deepfind.filesystem.watch;

import com.deepfind.filesystem.PathNormalizer;
import java.nio.file.Path;
import java.util.Objects;

public record FileWatchFailure(Path path, FileWatchFailureReason reason, String message) {

    public FileWatchFailure {
        path = PathNormalizer.absolute(path);
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
