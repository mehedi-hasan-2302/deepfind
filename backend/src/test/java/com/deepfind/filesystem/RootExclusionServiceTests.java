package com.deepfind.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepfind.persistence.ApplicationSettingsRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RootExclusionServiceTests {

    @TempDir
    Path root;

    @Test
    void normalizesDeduplicatesAndPersistsRelativePathsPerRoot() {
        MemorySettings settings = new MemorySettings();
        RootExclusionService exclusions = new RootExclusionService(settings);

        List<String> saved = exclusions.replace(
                root, List.of(" Private/Receipts ", "Private\\Receipts", "Archive/Old"), Instant.EPOCH);

        assertThat(saved)
                .containsExactly(
                        Path.of("Private", "Receipts").toString(),
                        Path.of("Archive", "Old").toString());
        assertThat(new RootExclusionService(settings).relativePaths(root)).containsExactlyElementsOf(saved);
        assertThat(exclusions.relativePaths(root.resolve("another-root"))).isEmpty();
    }

    @Test
    void combinesUserPathsWithSafeBuiltInExclusions() {
        RootExclusionService exclusions = new RootExclusionService(new MemorySettings());
        exclusions.replace(root, List.of("Private"), Instant.EPOCH);

        ExclusionPolicy policy = exclusions.policyFor(root);

        assertThat(policy.excludes(root, root.resolve("Private/secret.txt"))).isTrue();
        assertThat(policy.excludes(root, root.resolve("project/node_modules/library.js")))
                .isTrue();
        assertThat(policy.excludes(root, root.resolve("Public/notes.txt"))).isFalse();
    }

    @Test
    void rejectsPathsThatAreAbsoluteOrEscapeTheSelectedRoot() {
        RootExclusionService exclusions = new RootExclusionService(new MemorySettings());

        assertThatThrownBy(() -> exclusions.replace(root, List.of(root.toString()), Instant.EPOCH))
                .isInstanceOf(InvalidRootExclusionException.class)
                .hasMessageContaining("relative");
        assertThatThrownBy(() -> exclusions.replace(root, List.of("../outside"), Instant.EPOCH))
                .isInstanceOf(InvalidRootExclusionException.class)
                .hasMessageContaining("inside");
        assertThatThrownBy(() -> exclusions.replace(root, List.of("."), Instant.EPOCH))
                .isInstanceOf(InvalidRootExclusionException.class)
                .hasMessageContaining("inside");
    }

    private static final class MemorySettings implements ApplicationSettingsRepository {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> findValue(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, Instant updatedAt) {
            values.put(key, value);
        }
    }
}
