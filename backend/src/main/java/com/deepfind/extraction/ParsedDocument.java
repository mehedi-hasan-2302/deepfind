package com.deepfind.extraction;

import java.util.Objects;

public record ParsedDocument(String content, String mediaType, boolean truncated) {

    public ParsedDocument {
        content = Objects.requireNonNull(content, "content must not be null");
        mediaType = Objects.requireNonNull(mediaType, "mediaType must not be null");
    }
}
