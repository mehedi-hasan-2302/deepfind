package com.deepfind.persistence;

import com.deepfind.filesystem.PathNormalizer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentRootCatalog implements RootCatalog {

    static final String LAST_SELECTED_ROOT = "last_selected_root";

    private final IndexedRootRepository roots;
    private final ApplicationSettingsRepository settings;

    public PersistentRootCatalog(IndexedRootRepository roots, ApplicationSettingsRepository settings) {
        this.roots = roots;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Path> lastSelectedRoot() {
        return settings.findValue(LAST_SELECTED_ROOT)
                .flatMap(roots::findByPathKey)
                .filter(IndexedRootRecord::enabled)
                .map(IndexedRootRecord::absolutePath);
    }

    @Override
    @Transactional
    public void rememberSelected(Path root, Instant selectedAt) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        String pathKey = PathNormalizer.searchKey(absoluteRoot);
        roots.rememberSelected(pathKey, absoluteRoot, selectedAt);
        settings.put(LAST_SELECTED_ROOT, pathKey, selectedAt);
    }

    @Override
    @Transactional
    public void markIndexed(Path root, Instant indexedAt) {
        roots.markIndexed(PathNormalizer.searchKey(root), indexedAt);
    }
}
