package com.deepfind.api.index;

import java.util.List;

public record IndexExclusionsResponse(String root, List<String> paths) {

    public IndexExclusionsResponse {
        paths = List.copyOf(paths);
    }
}
