package com.deepfind.index;

import com.deepfind.filesystem.DiscoverySummary;

public record MetadataIndexingOutcome(DiscoverySummary discovery, long entriesIndexed) {}
