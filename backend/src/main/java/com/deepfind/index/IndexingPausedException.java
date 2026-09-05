package com.deepfind.index;

public final class IndexingPausedException extends RuntimeException {

    public IndexingPausedException() {
        super("Indexing paused at a safe entry boundary.");
    }
}
