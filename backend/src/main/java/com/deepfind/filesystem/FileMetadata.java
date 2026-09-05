package com.deepfind.filesystem;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record FileMetadata(
        Path absolutePath,
        String normalizedPath,
        String filename,
        String extension,
        FileSystemEntryKind kind,
        long sizeBytes,
        Instant modifiedAt,
        Instant createdAt) {

    public static FileMetadata from(Path path, BasicFileAttributes attributes) {
        Path absolutePath = PathNormalizer.absolute(path);
        Objects.requireNonNull(attributes, "attributes must not be null");
        FileSystemEntryKind kind = entryKind(attributes);
        String filename = filename(absolutePath);
        return new FileMetadata(
                absolutePath,
                PathNormalizer.searchKey(absolutePath),
                filename,
                extension(filename, kind),
                kind,
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                attributes.creationTime().toInstant());
    }

    private static FileSystemEntryKind entryKind(BasicFileAttributes attributes) {
        if (attributes.isSymbolicLink()) {
            return FileSystemEntryKind.SYMBOLIC_LINK;
        }
        if (attributes.isRegularFile()) {
            return FileSystemEntryKind.FILE;
        }
        if (attributes.isDirectory()) {
            return FileSystemEntryKind.DIRECTORY;
        }
        return FileSystemEntryKind.OTHER;
    }

    private static String filename(Path path) {
        Path filename = path.getFileName();
        return filename == null ? path.toString() : filename.toString();
    }

    private static String extension(String filename, FileSystemEntryKind kind) {
        if (kind == FileSystemEntryKind.DIRECTORY) {
            return "";
        }
        int separator = filename.lastIndexOf('.');
        return separator <= 0 || separator == filename.length() - 1
                ? ""
                : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
