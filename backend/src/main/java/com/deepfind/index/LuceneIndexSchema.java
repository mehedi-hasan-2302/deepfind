package com.deepfind.index;

final class LuceneIndexSchema {

    static final String VERSION = "1";
    static final String VERSION_KEY = "deepfind.schema.version";

    static final String PATH_KEY = "pathKey";
    static final String ABSOLUTE_PATH = "absolutePath";
    static final String FILENAME = "filename";
    static final String FILENAME_EXACT = "filenameExact";
    static final String PATH_TEXT = "pathText";
    static final String EXTENSION = "extension";
    static final String KIND = "kind";
    static final String SIZE_BYTES = "sizeBytes";
    static final String MODIFIED_AT = "modifiedAt";
    static final String CREATED_AT = "createdAt";
    static final String LAST_INDEXED_AT = "lastIndexedAt";

    private LuceneIndexSchema() {}
}
