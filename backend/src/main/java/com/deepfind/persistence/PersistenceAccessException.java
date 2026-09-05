package com.deepfind.persistence;

public class PersistenceAccessException extends RuntimeException {

    public PersistenceAccessException(String message) {
        super(message);
    }

    public PersistenceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
