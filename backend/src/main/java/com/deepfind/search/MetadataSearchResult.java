package com.deepfind.search;

import com.deepfind.filesystem.FileMetadata;

public record MetadataSearchResult(FileMetadata metadata, MetadataMatchType matchType) {}
