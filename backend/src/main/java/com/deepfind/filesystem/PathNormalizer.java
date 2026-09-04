package com.deepfind.filesystem;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class PathNormalizer {

    private static final boolean CASE_INSENSITIVE_PLATFORM = File.separatorChar == '\\';

    private PathNormalizer() {}

    public static Path absolute(Path path) {
        return Objects.requireNonNull(path, "path must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public static String searchKey(Path path) {
        String normalized = absolute(path).toString();
        return CASE_INSENSITIVE_PLATFORM ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    static String segmentKey(String segment) {
        return CASE_INSENSITIVE_PLATFORM ? segment.toLowerCase(Locale.ROOT) : segment;
    }

    public static boolean isCaseInsensitivePlatform() {
        return CASE_INSENSITIVE_PLATFORM;
    }
}
