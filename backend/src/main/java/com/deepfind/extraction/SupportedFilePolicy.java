package com.deepfind.extraction;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SupportedFilePolicy {

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt",
            "md",
            "java",
            "kt",
            "kts",
            "cs",
            "c",
            "h",
            "cpp",
            "hpp",
            "py",
            "js",
            "jsx",
            "ts",
            "tsx",
            "go",
            "rs",
            "rb",
            "php",
            "swift",
            "scala",
            "sh",
            "ps1",
            "sql",
            "xml",
            "json",
            "yaml",
            "yml",
            "toml",
            "ini",
            "properties",
            "css",
            "scss",
            "less");
    private static final Map<String, Set<String>> DOCUMENT_MEDIA_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    private static final Set<String> TEXTUAL_APPLICATION_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/x-sh",
            "application/x-shellscript");

    boolean supportsExtension(Path path) {
        String extension = extension(path);
        return PLAIN_TEXT_EXTENSIONS.contains(extension) || DOCUMENT_MEDIA_TYPES.containsKey(extension);
    }

    boolean supportsDetectedType(Path path, String mediaType) {
        String extension = extension(path);
        String normalizedMediaType = mediaType.toLowerCase(Locale.ROOT);
        Set<String> expectedDocumentTypes = DOCUMENT_MEDIA_TYPES.get(extension);
        if (expectedDocumentTypes != null) {
            return expectedDocumentTypes.contains(normalizedMediaType);
        }
        return PLAIN_TEXT_EXTENSIONS.contains(extension)
                && (normalizedMediaType.startsWith("text/") || TEXTUAL_APPLICATION_TYPES.contains(normalizedMediaType));
    }

    private static String extension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 || separator == name.length() - 1
                ? ""
                : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
