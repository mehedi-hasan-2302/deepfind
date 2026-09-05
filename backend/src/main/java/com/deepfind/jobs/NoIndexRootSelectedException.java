package com.deepfind.jobs;

public class NoIndexRootSelectedException extends RuntimeException {

    public NoIndexRootSelectedException() {
        super("Choose and index a folder before refreshing it.");
    }
}
