package com.deepfind.jobs;

public class IndexingAlreadyRunningException extends RuntimeException {

    public IndexingAlreadyRunningException() {
        super("An indexing job is already running.");
    }
}
