package com.deepfind.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepfind.extraction.ExtractionResult;
import com.deepfind.extraction.ExtractionStatus;
import com.deepfind.testing.LogCapture;
import org.junit.jupiter.api.Test;

class PrivacySafeDiagnosticsTests {

    @Test
    void logsOnlyStableExtractionStatusInsteadOfDocumentDerivedFields() {
        String privateMediaType = "application/private-customer-name";
        String privateReason = "C:\\Users\\person\\Salary expectation.txt contained secret-query";

        try (LogCapture logs = LogCapture.forClass(PrivacySafeDiagnosticsTests.class)) {
            PrivacySafeDiagnostics.logExtractionOutcome(
                    logs.logger(),
                    ExtractionResult.outcome(ExtractionStatus.PARSE_ERROR, privateMediaType, privateReason));

            assertThat(logs.messages())
                    .containsExactly("event=content_extraction_failed status=PARSE_ERROR")
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(privateMediaType, privateReason, "Salary expectation", "secret-query"));
        }
    }

    @Test
    void omitsRoutineSuccessAndUnsupportedExtractionOutcomes() {
        try (LogCapture logs = LogCapture.forClass(PrivacySafeDiagnosticsTests.class)) {
            PrivacySafeDiagnostics.logExtractionOutcome(
                    logs.logger(), ExtractionResult.outcome(ExtractionStatus.UNSUPPORTED, "", "PRIVATE_REASON"));

            assertThat(logs.messages()).isEmpty();
        }
    }
}
