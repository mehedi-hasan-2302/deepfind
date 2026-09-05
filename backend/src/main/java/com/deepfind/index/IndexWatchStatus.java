package com.deepfind.index;

import java.nio.file.Path;

public record IndexWatchStatus(Path root, IndexWatchState state, String message) {}
