package com.deepfind.filesystem;

public interface DiscoveryObserver {

    default void onEntry(FileMetadata metadata) {}

    default void onFailure(DiscoveryFailure failure) {}

    default void onProgress(DiscoveryProgress progress) {}
}
