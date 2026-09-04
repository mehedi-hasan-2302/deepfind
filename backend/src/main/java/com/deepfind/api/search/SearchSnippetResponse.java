package com.deepfind.api.search;

import com.deepfind.search.SearchSnippet;
import java.util.List;

public record SearchSnippetResponse(String text, List<SearchHighlightResponse> highlights) {

    public SearchSnippetResponse {
        highlights = List.copyOf(highlights);
    }

    static SearchSnippetResponse from(SearchSnippet snippet) {
        if (snippet == null) {
            return null;
        }
        return new SearchSnippetResponse(
                snippet.text(),
                snippet.highlights().stream().map(SearchHighlightResponse::from).toList());
    }
}
