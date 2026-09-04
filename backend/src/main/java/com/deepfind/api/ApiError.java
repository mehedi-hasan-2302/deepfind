package com.deepfind.api;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {

    public ApiError {
        details = Map.copyOf(details);
    }
}
