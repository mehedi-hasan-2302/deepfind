package com.deepfind.api.search;

import com.deepfind.search.TimedMetadataSearch;
import java.util.List;

public record SearchResponse(
        String query,
        long tookMs,
        long totalHits,
        boolean totalHitsExact,
        int offset,
        int limit,
        boolean hasMore,
        List<SearchResultResponse> results) {

    public SearchResponse {
        results = List.copyOf(results);
    }

    static SearchResponse from(TimedMetadataSearch search) {
        return new SearchResponse(
                search.query(),
                search.tookMs(),
                search.page().totalHits(),
                search.page().totalHitsExact(),
                search.page().offset(),
                search.page().limit(),
                search.page().hasMore(),
                search.page().results().stream().map(SearchResultResponse::from).toList());
    }
}
