package com.deepfind.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExclusionPolicyTests {

    @TempDir
    Path root;

    @Test
    void defaultPolicyExcludesNoiseSegmentsButNotTheSelectedRoot() {
        ExclusionPolicy policy = ExclusionPolicy.defaults();

        assertThat(policy.excludes(root, root)).isFalse();
        assertThat(policy.excludes(root, root.resolve("project/node_modules/library.js")))
                .isTrue();
        assertThat(policy.excludes(root, root.resolve("project/node_modules-notes.md")))
                .isFalse();
    }

    @Test
    void explicitPathExcludesThatPathAndItsDescendants() {
        Path privateDirectory = root.resolve("private");
        ExclusionPolicy policy = ExclusionPolicy.of(Set.of(), List.of(privateDirectory));

        assertThat(policy.excludes(root, privateDirectory)).isTrue();
        assertThat(policy.excludes(root, privateDirectory.resolve("nested/file.txt")))
                .isTrue();
        assertThat(policy.excludes(root, root.resolve("public/file.txt"))).isFalse();
    }
}
