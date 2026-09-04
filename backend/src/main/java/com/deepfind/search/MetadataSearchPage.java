package com.deepfind.search;

import java.util.List;

public record MetadataSearchPage(long totalHits, List<MetadataSearchResult> results) {

    public MetadataSearchPage {
        results = List.copyOf(results);
    }
}
