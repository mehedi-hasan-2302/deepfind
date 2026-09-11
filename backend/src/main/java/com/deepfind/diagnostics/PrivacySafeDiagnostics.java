package com.deepfind.diagnostics;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import java.util.Objects;
import org.slf4j.Logger;

public final class PrivacySafeDiagnostics {

    private PrivacySafeDiagnostics() {}

    public static void logExtractionOutcome(Logger logger, ExtractionResult result) {
        Objects.requireNonNull(logger, "logger must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (result.status() == ExtractionStatus.PARSE_ERROR
                || result.status() == ExtractionStatus.TIMEOUT
                || result.status() == ExtractionStatus.PERMISSION_DENIED) {
            logger.warn("event=content_extraction_failed status={}", result.status());
        }
    }

    public static String exceptionType(Throwable exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return exception.getClass().getSimpleName();
    }
}
