package com.deepfind.filesystem.watch;

public interface FileChangeObserver {

    default void onChange(FileChangeEvent event) {}

    default void onFailure(FileWatchFailure failure) {}
}
