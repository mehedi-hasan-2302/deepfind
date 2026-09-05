package com.deepfind.index;

import java.nio.file.Path;
import java.util.Optional;

public interface MetadataIndexSnapshot extends AutoCloseable {

    Optional<MetadataIndexEntry> find(Path path);

    @Override
    void close();
}
