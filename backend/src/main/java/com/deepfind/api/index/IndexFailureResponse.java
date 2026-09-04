package com.deepfind.api.index;

import com.deepfind.filesystem.DiscoveryFailure;

public record IndexFailureResponse(String path, String reason, String message) {

    static IndexFailureResponse from(DiscoveryFailure failure) {
        return failure == null
                ? null
                : new IndexFailureResponse(
                        failure.path().toString(), failure.reason().name(), failure.message());
    }
}
