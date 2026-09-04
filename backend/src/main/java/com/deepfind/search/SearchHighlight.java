package com.deepfind.search;

public record SearchHighlight(int start, int end) {

    public SearchHighlight {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("A highlight must have a non-empty, non-negative range.");
        }
    }
}
