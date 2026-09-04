package com.deepfind.platform;

public final class FileActionUnavailableException extends RuntimeException {

    public FileActionUnavailableException(String message) {
        super(message);
    }

    FileActionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
