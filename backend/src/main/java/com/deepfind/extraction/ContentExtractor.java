package com.deepfind.extraction;

import java.nio.file.Path;

public interface ContentExtractor {

    ExtractionResult extract(Path path);
}
