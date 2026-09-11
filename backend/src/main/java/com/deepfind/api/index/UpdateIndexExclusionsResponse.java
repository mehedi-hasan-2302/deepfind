package com.deepfind.api.index;

import java.util.List;

public record UpdateIndexExclusionsResponse(String root, List<String> paths, IndexStatusResponse reconciliation) {

    public UpdateIndexExclusionsResponse {
        paths = List.copyOf(paths);
    }
}
