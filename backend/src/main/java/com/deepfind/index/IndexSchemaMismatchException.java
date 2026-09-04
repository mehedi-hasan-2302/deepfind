package com.deepfind.index;

public class IndexSchemaMismatchException extends RuntimeException {

    public IndexSchemaMismatchException(String expectedVersion, String actualVersion) {
        super("Lucene index schema mismatch: expected " + expectedVersion + " but found " + actualVersion + ".");
    }
}
