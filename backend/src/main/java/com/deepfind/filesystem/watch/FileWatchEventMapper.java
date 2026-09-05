package com.deepfind.filesystem.watch;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

final class FileWatchEventMapper {

    private FileWatchEventMapper() {}

    static FileChangeKind map(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return FileChangeKind.CREATED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return FileChangeKind.MODIFIED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return FileChangeKind.DELETED;
        }
        if (kind == StandardWatchEventKinds.OVERFLOW) {
            return FileChangeKind.OVERFLOW;
        }
        throw new IllegalArgumentException("Unsupported filesystem watch event kind.");
    }
}
