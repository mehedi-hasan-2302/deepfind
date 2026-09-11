package com.deepfind.filesystem;

import com.deepfind.persistence.ApplicationSettingsRepository;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class RootExclusionService {

    public static final int MAX_EXCLUSIONS = 100;
    public static final int MAX_RELATIVE_PATH_LENGTH = 500;
    private static final String SETTING_PREFIX = "root_exclusions:";

    private final ApplicationSettingsRepository settings;

    public RootExclusionService(ApplicationSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    public List<String> relativePaths(Path root) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        return settings.findValue(settingKey(absoluteRoot)).stream()
                .flatMap(value -> value.lines())
                .filter(value -> !value.isBlank())
                .toList();
    }

    public ExclusionPolicy policyFor(Path root) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        List<Path> paths = relativePaths(absoluteRoot).stream()
                .map(absoluteRoot::resolve)
                .map(Path::normalize)
                .toList();
        return ExclusionPolicy.defaultsWithPaths(paths);
    }

    public List<String> replace(Path root, Collection<String> requestedPaths, Instant updatedAt) {
        Path absoluteRoot = PathNormalizer.absolute(root);
        Objects.requireNonNull(requestedPaths, "requestedPaths must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (requestedPaths.size() > MAX_EXCLUSIONS) {
            throw new InvalidRootExclusionException("At most " + MAX_EXCLUSIONS + " exclusions are allowed.");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (String requestedPath : requestedPaths) {
            String relative = normalizeRelativePath(absoluteRoot, requestedPath);
            normalized.putIfAbsent(PathNormalizer.searchKey(absoluteRoot.resolve(relative)), relative);
        }
        List<String> paths = new ArrayList<>(normalized.values());
        settings.put(settingKey(absoluteRoot), String.join("\n", paths), updatedAt);
        return List.copyOf(paths);
    }

    private static String normalizeRelativePath(Path root, String requestedPath) {
        String value = Objects.requireNonNull(requestedPath, "excluded path must not be null")
                .trim();
        if (value.isEmpty()
                || value.length() > MAX_RELATIVE_PATH_LENGTH
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRootExclusionException(
                    "Excluded paths must be non-empty relative paths of at most 500 characters.");
        }
        String platformPath = value.replace('\\', File.separatorChar).replace('/', File.separatorChar);
        try {
            Path parsed = Path.of(platformPath);
            if (parsed.isAbsolute()) {
                throw new InvalidRootExclusionException("Excluded paths must be relative to the selected folder.");
            }
            Path absolute = root.resolve(parsed).normalize();
            if (absolute.equals(root) || !absolute.startsWith(root)) {
                throw new InvalidRootExclusionException("Excluded paths must stay inside the selected folder.");
            }
            return root.relativize(absolute).toString();
        } catch (InvalidPathException exception) {
            throw new InvalidRootExclusionException("An excluded path is invalid.", exception);
        }
    }

    private static String settingKey(Path root) {
        return SETTING_PREFIX + PathNormalizer.searchKey(root);
    }
}
