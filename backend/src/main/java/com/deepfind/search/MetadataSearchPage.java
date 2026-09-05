package com.deepfind.search;

import java.util.List;

public record MetadataSearchPage(
        long totalHits,
        boolean totalHitsExact,
        int offset,
        int limit,
        boolean hasMore,
        List<MetadataSearchResult> results) {

    public MetadataSearchPage {
        results = List.copyOf(results);
    }
}
