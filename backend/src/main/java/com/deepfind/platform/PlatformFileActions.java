package com.deepfind.platform;

import static com.deepfind.diagnostics.PrivacySafeDiagnostics.exceptionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class PlatformFileActions implements FileActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformFileActions.class);

    private static final String UNAVAILABLE_MESSAGE =
            "DeepFind could not ask the operating system to complete this action.";

    private final OperatingSystem operatingSystem;
    private final SystemProcessLauncher launcher;

    public PlatformFileActions() {
        this(OperatingSystem.current(), PlatformFileActions::startProcess);
    }

    PlatformFileActions(OperatingSystem operatingSystem, SystemProcessLauncher launcher) {
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem must not be null");
        this.launcher = Objects.requireNonNull(launcher, "launcher must not be null");
    }

    @Override
    public void open(Path path) {
        Path target = validate(path);
        launch("OPEN", openCommand(target));
    }

    @Override
    public void reveal(Path path) {
        Path target = validate(path);
        launch("REVEAL", revealCommand(target));
    }

    private Path validate(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!path.isAbsolute()) {
            throw new InvalidFileActionException("Choose an existing absolute file or folder path.");
        }
        Path normalized = path.normalize();
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidFileActionException("This file or folder no longer exists.");
        }
        return normalized;
    }

    private List<String> openCommand(Path target) {
        return switch (operatingSystem) {
            case WINDOWS -> List.of("explorer.exe", target.toString());
            case MACOS -> List.of("open", target.toString());
            case LINUX -> List.of("xdg-open", target.toString());
            case UNSUPPORTED -> throw new FileActionUnavailableException(UNAVAILABLE_MESSAGE);
        };
    }

    private List<String> revealCommand(Path target) {
        return switch (operatingSystem) {
            case WINDOWS -> List.of("explorer.exe", "/select," + target);
            case MACOS -> List.of("open", "-R", target.toString());
            case LINUX -> List.of("xdg-open", revealDirectory(target).toString());
            case UNSUPPORTED -> throw new FileActionUnavailableException(UNAVAILABLE_MESSAGE);
        };
    }

    private static Path revealDirectory(Path target) {
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            return target;
        }
        Path parent = target.getParent();
        return parent == null ? target : parent;
    }

    private void launch(String action, List<String> command) {
        try {
            launcher.launch(command);
        } catch (IOException | SecurityException exception) {
            LOGGER.error("event=platform_action_failed action={} exception={}", action, exceptionType(exception));
            throw new FileActionUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    private static void startProcess(List<String> command) throws IOException {
        new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
