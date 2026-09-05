package com.deepfind.filesystem.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.StandardWatchEventKinds;
import org.junit.jupiter.api.Test;

class FileWatchEventMapperTests {

    @Test
    void mapsAllStandardEventKindsIncludingOverflow() {
        assertThat(FileWatchEventMapper.map(StandardWatchEventKinds.ENTRY_CREATE))
                .isEqualTo(FileChangeKind.CREATED);
        assertThat(FileWatchEventMapper.map(StandardWatchEventKinds.ENTRY_MODIFY))
                .isEqualTo(FileChangeKind.MODIFIED);
        assertThat(FileWatchEventMapper.map(StandardWatchEventKinds.ENTRY_DELETE))
                .isEqualTo(FileChangeKind.DELETED);
        assertThat(FileWatchEventMapper.map(StandardWatchEventKinds.OVERFLOW)).isEqualTo(FileChangeKind.OVERFLOW);
    }
}
