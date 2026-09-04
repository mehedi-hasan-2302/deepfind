package com.deepfind.api.search;

import com.deepfind.search.MetadataSearchResult;
import java.time.Instant;

public record SearchResultResponse(
        String path,
        String filename,
        String extension,
        String type,
        long sizeBytes,
        Instant modifiedAt,
        String matchType,
        SearchSnippetResponse snippet) {

    static SearchResultResponse from(MetadataSearchResult result) {
        var metadata = result.metadata();
        return new SearchResultResponse(
                metadata.absolutePath().toString(),
                metadata.filename(),
                metadata.extension(),
                metadata.kind().name(),
                metadata.sizeBytes(),
                metadata.modifiedAt(),
                result.matchType().name(),
                SearchSnippetResponse.from(result.snippet()));
    }
}
