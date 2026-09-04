package com.deepfind.search;

public record TimedMetadataSearch(String query, long tookMs, MetadataSearchPage page) {}
