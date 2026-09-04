package com.deepfind.extraction;

import java.util.Objects;

public record ExtractionResult(
        ExtractionStatus status, String content, String mediaType, boolean truncated, String reason) {

    public ExtractionResult {
        status = Objects.requireNonNull(status, "status must not be null");
        content = content == null ? "" : content;
        mediaType = mediaType == null ? "" : mediaType;
        reason = reason == null ? "" : reason;
        if (status != ExtractionStatus.SUCCESS && !content.isEmpty()) {
            throw new IllegalArgumentException("Only successful extraction may include content.");
        }
    }

    public static ExtractionResult success(ParsedDocument document) {
        return new ExtractionResult(
                ExtractionStatus.SUCCESS,
                document.content(),
                document.mediaType(),
                document.truncated(),
                document.truncated() ? "CHARACTER_LIMIT_REACHED" : "");
    }

    public static ExtractionResult outcome(ExtractionStatus status, String mediaType, String reason) {
        return new ExtractionResult(status, "", mediaType, false, reason);
    }
}
