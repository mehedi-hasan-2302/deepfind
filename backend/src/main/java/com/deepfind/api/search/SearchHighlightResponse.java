package com.deepfind.api.search;

import com.deepfind.search.SearchHighlight;

public record SearchHighlightResponse(int start, int end) {

    static SearchHighlightResponse from(SearchHighlight highlight) {
        return new SearchHighlightResponse(highlight.start(), highlight.end());
    }
}
