package com.deepfind.filesystem.watch;

public class FileWatchStartupException extends RuntimeException {

    public FileWatchStartupException(String message) {
        super(message);
    }

    public FileWatchStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
