package com.deepfind.index;

import com.deepfind.filesystem.DiscoveryFailure;
import com.deepfind.filesystem.DiscoveryObserver;
import com.deepfind.filesystem.DiscoveryProgress;
import com.deepfind.filesystem.DiscoverySummary;
import com.deepfind.filesystem.ExclusionPolicy;
import com.deepfind.filesystem.FileMetadata;
import com.deepfind.filesystem.FileSystemDiscoveryService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class MetadataIndexingService {

    private final FileSystemDiscoveryService discoveryService;
    private final LuceneMetadataIndex index;

    public MetadataIndexingService(FileSystemDiscoveryService discoveryService, LuceneMetadataIndex index) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService must not be null");
        this.index = Objects.requireNonNull(index, "index must not be null");
    }

    public MetadataIndexingOutcome indexRoot(Path root, ExclusionPolicy exclusions, DiscoveryObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        AtomicLong indexed = new AtomicLong();
        DiscoverySummary summary = discoveryService.discover(root, exclusions, new DiscoveryObserver() {
            @Override
            public void onEntry(FileMetadata metadata) {
                index.upsert(metadata);
                indexed.incrementAndGet();
                observer.onEntry(metadata);
            }

            @Override
            public void onFailure(DiscoveryFailure failure) {
                observer.onFailure(failure);
            }

            @Override
            public void onProgress(DiscoveryProgress progress) {
                observer.onProgress(progress);
            }
        });
        index.commit();
        return new MetadataIndexingOutcome(summary, indexed.get());
    }
}
