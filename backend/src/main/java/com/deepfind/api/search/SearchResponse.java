package com.deepfind.api.search;

import com.deepfind.search.TimedMetadataSearch;
import java.util.List;

public record SearchResponse(String query, long tookMs, long totalHits, List<SearchResultResponse> results) {

    public SearchResponse {
        results = List.copyOf(results);
    }

    static SearchResponse from(TimedMetadataSearch search) {
        return new SearchResponse(
                search.query(),
                search.tookMs(),
                search.page().totalHits(),
                search.page().results().stream().map(SearchResultResponse::from).toList());
    }
}
