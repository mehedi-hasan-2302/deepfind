package com.deepfind.search;

import com.deepfind.index.LuceneMetadataIndex;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MetadataSearchService {

    private final LuceneMetadataIndex index;

    public MetadataSearchService(LuceneMetadataIndex index) {
        this.index = Objects.requireNonNull(index, "index must not be null");
    }

    public TimedMetadataSearch search(String query, int limit) {
        long started = System.nanoTime();
        MetadataSearchPage page = index.searchPage(query, limit);
        long tookMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new TimedMetadataSearch(query.trim(), tookMs, page);
    }
}
