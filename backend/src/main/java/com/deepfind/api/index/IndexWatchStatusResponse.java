package com.deepfind.api.index;

import com.deepfind.index.IndexWatchStatus;

public record IndexWatchStatusResponse(String root, String state, String message) {

    static IndexWatchStatusResponse from(IndexWatchStatus status) {
        return new IndexWatchStatusResponse(
                status.root() == null ? null : status.root().toString(),
                status.state().name(),
                status.message());
    }
}
