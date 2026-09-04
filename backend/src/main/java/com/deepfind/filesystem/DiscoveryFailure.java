package com.deepfind.filesystem;

import java.nio.file.Path;

public record DiscoveryFailure(Path path, DiscoveryFailureReason reason, String message) {}
