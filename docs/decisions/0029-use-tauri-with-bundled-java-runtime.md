# 0029 — Use Tauri with a bundled Java runtime

## Context

DeepFind currently has a working Vite/React interface and executable Spring Boot JAR. A release user must not install Java, Node.js, Maven, Rust, or start a server manually. The desktop package must own one window, backend readiness, clean shutdown, icons, local data behavior, and a single-click Windows installer while preserving the audited local network boundary.

The credible shell choices are Tauri, Electron, JavaFX with `jpackage`, or opening the existing web application in the user's browser. Each can produce an executable, but their runtime size, security boundary, UI compatibility, and lifecycle cost differ materially.

## Decision

Use Tauri 2 as the desktop shell. Keep React as the UI and Spring Boot as a separately supervised local child process.

Bundle the Spring Boot JAR and an application-specific Java 21 runtime produced by `jlink` as Tauri resources. The Rust core resolves and starts the exact bundled Java executable without exposing a generic shell plugin or process API to the frontend. The production JAR serves the compiled React assets, allowing the WebView and `/api` to share one dynamically selected `127.0.0.1` origin without CORS expansion.

The Rust supervisor retains the child handle, waits for loopback health before revealing the main window, restricts navigation and new-window behavior to the exact local origin, reports backend failure locally, and requests graceful shutdown through a private inherited channel before a bounded forced termination. Keep the existing `${user.home}/.deepfind` state location for the first packaged release.

Produce a per-user NSIS `DeepFind-Setup.exe` and installed `DeepFind.exe`. Use Tauri's WebView2 `offlineInstaller` mode so installation itself does not need network access. Do not include an updater. Use the narrowest Tauri capability set and a restrictive content security policy; the React application currently needs no Tauri native API.

Treat `jdeps` output as a starting point rather than proof that a minimized runtime is complete. Accept the `jlink` image only after full feature and clean-machine tests. Preserve the broader-runtime fallback if service loading, reflection, native SQLite, document parsing, or platform behavior is incomplete.

## Alternatives considered

- Electron: rejected for the MVP because it would add bundled Chromium and Node plus their privileged maintenance surface while Java still needs to be bundled. It remains a fallback if the Tauri/WebView2 spike fails.
- JavaFX plus `jpackage`: rejected because it changes the established browser UI boundary and introduces JavaFX WebView compatibility/native packaging work. `jpackage` remains useful for runtime experiments but will not own the final shell.
- `jpackage` plus the system browser: rejected because browser and server lifecycle would not feel like one installed desktop product.
- Rewrite the backend in Rust: rejected as an unrelated high-risk replacement of tested Lucene, Tika, SQLite, watcher, and API behavior.
- Require an installed Java or downloadable WebView bootstrapper: rejected because Phase 8 requires a self-contained, offline-capable user installation.

## Consequences

The implementation adds Rust/Tauri to the build toolchain but not as a user prerequisite. The package keeps the working React and Java application boundaries, introduces a small native lifecycle owner, and can create the requested setup executable without administrator elevation.

There will be two application processes plus WebView2 renderer processes. Backend startup, port discovery, crash recovery, shutdown, stale-child cleanup, navigation policy, runtime minimization, WebView2 installation, and code-signing behavior all require explicit tests. Bundling the WebView2 offline installer increases setup size substantially, and bundling Java remains a major part of the installed footprint.

The desktop shell and dependency upgrades invalidate the Phase 7 packaged-runtime audit until it is rerun against the installed application. Code signing remains blocked on a publisher certificate and is not silently simulated.
