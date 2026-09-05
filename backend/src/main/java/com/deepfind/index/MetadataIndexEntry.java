package com.deepfind.index;

import com.deepfind.filesystem.FileMetadata;

public record MetadataIndexEntry(FileMetadata metadata, boolean contentAttempted) {}
