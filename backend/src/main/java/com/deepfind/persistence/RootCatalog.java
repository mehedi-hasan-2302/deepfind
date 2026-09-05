package com.deepfind.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public interface RootCatalog {

    Optional<Path> lastSelectedRoot();

    void rememberSelected(Path root, Instant selectedAt);

    void markIndexed(Path root, Instant indexedAt);
}
