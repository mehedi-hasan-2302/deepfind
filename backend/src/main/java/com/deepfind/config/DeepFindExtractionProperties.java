package com.deepfind.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "deepfind.extraction")
public record DeepFindExtractionProperties(
        @Min(1) long maxFileSizeBytes,
        @Min(1) int maxExtractedCharacters,
        @NotNull Duration timeout,
        @Min(1) int workerCount,
        @Min(1) int queueCapacity) {

    public DeepFindExtractionProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("Extraction timeout must be positive.");
        }
    }
}
