package com.deepfind.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "deepfind.reconciliation")
public record DeepFindReconciliationProperties(@NotNull Duration interval) {

    public DeepFindReconciliationProperties {
        if (interval != null && (interval.isZero() || interval.isNegative())) {
            throw new IllegalArgumentException("Reconciliation interval must be positive.");
        }
    }
}
