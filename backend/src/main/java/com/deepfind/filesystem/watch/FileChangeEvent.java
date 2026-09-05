package com.deepfind.filesystem.watch;

import com.deepfind.filesystem.PathNormalizer;
import java.nio.file.Path;
import java.util.Objects;

public record FileChangeEvent(Path path, FileChangeKind kind) {

    public FileChangeEvent {
        path = PathNormalizer.absolute(path);
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
