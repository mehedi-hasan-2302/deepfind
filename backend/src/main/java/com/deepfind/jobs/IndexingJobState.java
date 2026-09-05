package com.deepfind.jobs;

public enum IndexingJobState {
    IDLE,
    RUNNING,
    PAUSING,
    PAUSED,
    COMPLETED,
    FAILED,
    INTERRUPTED
}
