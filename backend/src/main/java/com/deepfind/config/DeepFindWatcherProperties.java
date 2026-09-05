package com.deepfind.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "deepfind.watcher")
public record DeepFindWatcherProperties(
        @Min(1) int queueCapacity, @NotNull Duration shutdownTimeout) {

    public DeepFindWatcherProperties {
        if (shutdownTimeout != null && (shutdownTimeout.isZero() || shutdownTimeout.isNegative())) {
            throw new IllegalArgumentException("Watcher shutdown timeout must be positive.");
        }
    }
}
