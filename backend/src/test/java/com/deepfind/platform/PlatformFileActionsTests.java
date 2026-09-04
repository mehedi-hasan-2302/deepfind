package com.deepfind.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformFileActionsTests {

    @TempDir
    Path root;

    @Test
    void buildsWindowsOpenAndRevealCommandsWithoutUsingAShell() throws IOException {
        Path file = Files.writeString(root.resolve("quarterly report.txt"), "private");
        RecordingLauncher launcher = new RecordingLauncher();
        PlatformFileActions actions = new PlatformFileActions(OperatingSystem.WINDOWS, launcher);

        actions.open(file);
        actions.reveal(file);

        assertThat(launcher.commands)
                .containsExactly(List.of("explorer.exe", file.toString()), List.of("explorer.exe", "/select," + file));
    }

    @Test
    void usesNativeMacRevealAndLinuxContainingDirectoryCommands() throws IOException {
        Path file = Files.writeString(root.resolve("notes.md"), "private");
        RecordingLauncher macLauncher = new RecordingLauncher();
        RecordingLauncher linuxLauncher = new RecordingLauncher();

        new PlatformFileActions(OperatingSystem.MACOS, macLauncher).reveal(file);
        new PlatformFileActions(OperatingSystem.LINUX, linuxLauncher).reveal(file);

        assertThat(macLauncher.commands).containsExactly(List.of("open", "-R", file.toString()));
        assertThat(linuxLauncher.commands).containsExactly(List.of("xdg-open", root.toString()));
    }

    @Test
    void rejectsRelativeAndMissingPathsBeforeLaunchingAnything() {
        RecordingLauncher launcher = new RecordingLauncher();
        PlatformFileActions actions = new PlatformFileActions(OperatingSystem.WINDOWS, launcher);

        assertThatThrownBy(() -> actions.open(Path.of("relative.txt")))
                .isInstanceOf(InvalidFileActionException.class)
                .hasMessage("Choose an existing absolute file or folder path.");
        assertThatThrownBy(() -> actions.reveal(root.resolve("missing.txt")))
                .isInstanceOf(InvalidFileActionException.class)
                .hasMessage("This file or folder no longer exists.");
        assertThat(launcher.commands).isEmpty();
    }

    @Test
    void translatesProcessFailuresIntoASafeApplicationError() throws IOException {
        Path file = Files.writeString(root.resolve("locked.txt"), "private");
        PlatformFileActions actions = new PlatformFileActions(OperatingSystem.WINDOWS, command -> {
            throw new IOException("sensitive system detail");
        });

        assertThatThrownBy(() -> actions.open(file))
                .isInstanceOf(FileActionUnavailableException.class)
                .hasMessage("DeepFind could not ask the operating system to complete this action.");
    }

    private static final class RecordingLauncher implements SystemProcessLauncher {

        private final List<List<String>> commands = new ArrayList<>();

        @Override
        public void launch(List<String> command) {
            commands.add(List.copyOf(command));
        }
    }
}
