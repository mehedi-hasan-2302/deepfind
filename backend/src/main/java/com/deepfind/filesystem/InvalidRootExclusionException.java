package com.deepfind.filesystem;

public final class InvalidRootExclusionException extends IllegalArgumentException {

    public InvalidRootExclusionException(String message) {
        super(message);
    }

    public InvalidRootExclusionException(String message, Throwable cause) {
        super(message, cause);
    }
}
