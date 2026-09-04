package com.deepfind.health;

import java.time.Instant;

public record HealthResponse(String status, Instant timestamp) {}
