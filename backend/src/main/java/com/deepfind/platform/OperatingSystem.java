package com.deepfind.platform;

import java.util.Locale;

enum OperatingSystem {
    WINDOWS,
    MACOS,
    LINUX,
    UNSUPPORTED;

    static OperatingSystem current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac")) {
            return MACOS;
        }
        if (name.contains("nux") || name.contains("nix") || name.contains("aix")) {
            return LINUX;
        }
        return UNSUPPORTED;
    }
}
