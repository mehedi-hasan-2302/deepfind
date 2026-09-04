package com.deepfind.filesystem;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ExclusionPolicy {

    private static final Set<String> DEFAULT_SEGMENTS =
            Set.of(".git", "node_modules", "target", "build", "dist", ".gradle");

    private final Set<String> excludedSegmentKeys;
    private final List<Path> excludedPaths;

    private ExclusionPolicy(Collection<String> excludedSegments, Collection<Path> excludedPaths) {
        this.excludedSegmentKeys = excludedSegments.stream()
                .map(segment -> Objects.requireNonNull(segment, "excluded segment must not be null"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .map(PathNormalizer::segmentKey)
                .collect(Collectors.toUnmodifiableSet());
        this.excludedPaths = excludedPaths.stream()
                .map(path -> Objects.requireNonNull(path, "excluded path must not be null"))
                .map(PathNormalizer::absolute)
                .toList();
    }

    public static ExclusionPolicy defaults() {
        return of(DEFAULT_SEGMENTS, List.of());
    }

    public static ExclusionPolicy none() {
        return of(Set.of(), List.of());
    }

    public static ExclusionPolicy of(Collection<String> excludedSegments, Collection<Path> excludedPaths) {
        return new ExclusionPolicy(
                Objects.requireNonNull(excludedSegments, "excludedSegments must not be null"),
                Objects.requireNonNull(excludedPaths, "excludedPaths must not be null"));
    }

    public boolean excludes(Path root, Path candidate) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        Path absoluteCandidate = PathNormalizer.absolute(candidate);

        if (excludedPaths.stream()
                .anyMatch(excludedPath ->
                        absoluteCandidate.equals(excludedPath) || absoluteCandidate.startsWith(excludedPath))) {
            return true;
        }

        if (!absoluteCandidate.startsWith(absoluteRoot) || absoluteCandidate.equals(absoluteRoot)) {
            return false;
        }

        Path relative = absoluteRoot.relativize(absoluteCandidate);
        for (Path segment : relative) {
            if (excludedSegmentKeys.contains(PathNormalizer.segmentKey(segment.toString()))) {
                return true;
            }
        }
        return false;
    }
}
