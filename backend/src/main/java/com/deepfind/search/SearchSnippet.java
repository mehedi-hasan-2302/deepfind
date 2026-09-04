package com.deepfind.search;

import java.util.List;
import java.util.Objects;

public record SearchSnippet(String text, List<SearchHighlight> highlights) {

    public SearchSnippet {
        text = Objects.requireNonNull(text, "text must not be null");
        highlights = List.copyOf(highlights);
        int previousEnd = 0;
        for (SearchHighlight highlight : highlights) {
            if (highlight.start() < previousEnd || highlight.end() > text.length()) {
                throw new IllegalArgumentException("Highlights must be ordered, non-overlapping, and inside the text.");
            }
            previousEnd = highlight.end();
        }
    }
}
