package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public interface IndexedRootRepository {

    void rememberSelected(String pathKey, Path absolutePath, Instant selectedAt);

    void markIndexed(String pathKey, Instant indexedAt);

    Optional<IndexedRootRecord> findByPathKey(String pathKey);
}
