package com.deepfind.platform;

import java.nio.file.Path;

public interface FileActions {

    void open(Path path);

    void reveal(Path path);
}
