package com.deepfind.extraction;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentParser {

    String detectMediaType(Path path) throws IOException;

    ParsedDocument parse(Path path, int characterLimit) throws IOException, DocumentParsingException;
}
