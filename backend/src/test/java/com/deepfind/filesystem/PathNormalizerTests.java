package com.deepfind.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PathNormalizerTests {

    @Test
    void producesAnAbsoluteNormalizedPath() {
        Path input = Path.of("folder", "..", "folder", "file.txt");

        Path normalized = PathNormalizer.absolute(input);

        assertThat(normalized).isAbsolute();
        assertThat(normalized.toString()).doesNotContain("..");
        assertThat(normalized.endsWith(Path.of("folder", "file.txt"))).isTrue();
    }

    @Test
    void searchKeyUsesPlatformCaseRules() {
        String key = PathNormalizer.searchKey(Path.of("MixedCase", "File.TXT"));

        if (PathNormalizer.isCaseInsensitivePlatform()) {
            assertThat(key).doesNotContain("MixedCase").doesNotContain("File.TXT");
        } else {
            assertThat(key).contains("MixedCase").contains("File.TXT");
        }
    }
}
