# Desktop Packaging Plan

## What the Windows `.exe` means

DeepFind will produce two related executables:

- `DeepFind.exe`: the installed Tauri desktop application that owns the window and backend lifecycle.
- `DeepFind-Setup.exe`: the per-user NSIS installer that places the application, bundled Java runtime, backend, frontend assets, icon, and uninstaller on the computer.

The installer is not the application itself. Building only an installer around the current JAR would still leave the user without a desktop window and would not supervise startup or shutdown.

## Selected architecture

Use Tauri 2 as the Windows desktop shell and bundle an application-specific Java 21 runtime produced with `jlink`.

```text
DeepFind-Setup.exe
└── DeepFind.exe                 Tauri/Rust lifecycle owner
    ├── WebView2                 React user interface
    └── resources/
        ├── runtime/bin/java.exe bundled Java 21 runtime
        └── deepfind-backend.jar Spring Boot + compiled frontend
                  │
                  └── 127.0.0.1:<ephemeral-port>
                      ├── /              packaged React assets
                      └── /api/...       existing local API
```

The Tauri core will resolve exact packaged resource paths and start only the bundled Java executable. It will not expose a generic shell/process capability to frontend JavaScript. The React interface does not need Tauri filesystem or process APIs.

The production Spring Boot JAR will serve the compiled React assets as well as `/api`. After the backend reports its ephemeral loopback port, Tauri will create the WebView at that exact origin. Existing relative `/api` requests therefore remain same-origin and require no broad CORS rule. Navigation, new-window creation, downloads, and Tauri capabilities will be denied unless a later feature justifies a narrow exception.

Local application state will continue to default to `${user.home}/.deepfind`, preserving the documented development behavior and avoiding an unnecessary data migration during packaging.

## Why Tauri 2

| Option | Advantages | Costs and risks | Decision |
| --- | --- | --- | --- |
| Tauri 2 | Reuses Vite/React; native window; small shell; Rust-owned lifecycle; granular capabilities; NSIS `.exe` and MSI support | Adds a Rust build toolchain; requires WebView2; still must supervise the Java process | Selected |
| Electron | Reuses React; mature packaging and child-process APIs; bundles a consistent Chromium renderer | Ships Chromium and Node, adds a larger privileged dependency/update surface, and still must bundle Java | Rejected for the MVP |
| JavaFX + `jpackage` | One language/runtime; JDK already supplies `jpackage`; lifecycle could share the JVM | Requires replacing or adapting the browser shell to JavaFX WebView, adds JavaFX native packaging, and risks Web API incompatibilities | Rejected because it changes the working UI architecture |
| JAR/`jpackage` plus system browser | Simplest native Java launcher and runtime bundle | Does not feel like one desktop product, leaves browser lifecycle/port behavior exposed, and does not meet the intended desktop-window experience | Rejected |

Tauri is licensed under MIT or Apache-2.0 and uses the operating-system WebView rather than embedding a private Chromium copy. Its official resource mechanism supports bundling additional files such as the Java runtime and backend. Relevant references: [Tauri architecture](https://tauri.app/concept/architecture/), [capabilities](https://v2.tauri.app/security/capabilities/), [embedded resources](https://v2.tauri.app/develop/resources/), and [Windows installers](https://v2.tauri.app/distribute/windows-installer/).

Electron remains a credible fallback if WebView2 compatibility or Tauri process supervision fails during the spike. Electron's official security guidance requires sandboxed renderers, context isolation, restrictive navigation and permissions, and careful handling of the privileged main process: [Electron process model](https://www.electronjs.org/docs/latest/tutorial/process-model) and [security checklist](https://www.electronjs.org/docs/latest/tutorial/security).

## Runtime and installer choices

### Java runtime

Use `jdeps` to establish a candidate module set and `jlink` to create the application runtime. Reflection and service loading can make static module detection incomplete, so the result is accepted only after the full backend suite, packaged launch, representative PDF/DOCX indexing, SQLite migration, Lucene reopen, filesystem watching, and clean-machine testing pass. Falling back to a broader Java runtime is preferable to shipping a deceptively small but broken image.

`jpackage` remains useful as a diagnostic/reference tool, but Tauri will own the final installer. Oracle documents that `jpackage` creates Windows `exe`/`msi` packages and uses `jlink` for self-contained runtimes when one is not supplied: [JDK 21 `jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

### WebView2

Windows 10/11 commonly carry the Evergreen WebView2 runtime, but it is not guaranteed on every clean Windows, Server, or LTSC installation. The release installer will therefore use Tauri's `offlineInstaller` mode rather than a download bootstrapper. Tauri currently documents about 127 MB of additional installer size for this mode. This preserves offline installation while allowing the installed Evergreen runtime to receive Microsoft security servicing. WebView2's operating-system-level servicing may use Microsoft's update mechanism independently of DeepFind; it is not a DeepFind updater and does not receive documents, queries, or index data. See [Tauri WebView2 installation modes](https://v2.tauri.app/distribute/windows-installer/) and [Microsoft's WebView2 runtime guidance](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/evergreen-vs-fixed-version).

### Installation scope and updates

Use a per-user NSIS installation under `%LOCALAPPDATA%`. It needs no administrator elevation and produces the requested setup `.exe`. Do not add an automatic updater in the MVP; updates remain explicit installer downloads until a separately reviewed, disclosed, narrowly networked update design exists.

Code signing is not required to prove packaging, but an unsigned installer can trigger Windows reputation warnings. Signing requires a publisher identity and certificate supplied by the project owner, so unsigned development artifacts will be clearly labeled until that exists.

## Backend lifecycle protocol

1. Tauri resolves the bundled Java runtime and backend JAR by resource APIs, never from `PATH`.
2. It starts Java without a visible console, binds Spring Boot to `127.0.0.1` on an ephemeral port, captures only privacy-safe diagnostics, and retains the child handle.
3. The backend reports its chosen port through a supervisor-owned local handshake channel; Tauri validates loopback health before showing the main window.
4. The WebView is allowed to navigate only within that exact loopback origin. The frontend receives no generic native process/filesystem capability.
5. Closing DeepFind requests graceful Spring shutdown through the private supervisor channel, waits for Lucene/SQLite/watcher cleanup, and forcibly ends only the child process after a bounded deadline.
6. Unexpected backend exit replaces the search view with a local recovery message. The shell never silently falls back to a hosted page.

The handshake mechanism will be selected during implementation. A captured stdout event or inherited stdin control is preferable to a public shutdown HTTP route. A random port reduces collision risk but is not authentication against another process running as the same user.

## Implementation modules

1. **Bootstrap the shell.** Install the Rust/Tauri build prerequisites, create the smallest Tauri 2 shell, use local bundled assets only, and prove a development window opens.
2. **Create the desktop backend artifact.** Add a desktop build profile that compiles the frontend into the Spring Boot JAR and produce a tested `jlink` Java runtime.
3. **Implement supervision.** Start the bundled backend invisibly, discover its ephemeral port, wait for health, restrict navigation, and display startup/recovery states.
4. **Implement clean lifecycle.** Add graceful supervisor shutdown, bounded forced cleanup, crash reporting without private paths, and single-instance behavior.
5. **Brand and install.** Add the icon and Windows metadata, configure per-user NSIS plus offline WebView2, and generate `DeepFind.exe` and `DeepFind-Setup.exe`.
6. **Verify the deliverable.** Test on a clean Windows VM without Java, Node, Rust, or internet; exercise indexing/search/open/reveal/restart/uninstall; confirm data preservation, process cleanup, and runtime network behavior.

## Acceptance evidence

The locally verified unsigned 0.1.0 installer is available. See [the exact artifact and test record](WINDOWS_PREVIEW_VERIFICATION.md) and [installation/build instructions](INSTALLATION.md). This preview does not yet satisfy the clean-machine and full manual UI release gates below.

Phase 8 is complete only when:

- installation and first launch work without developer tools or an internet connection;
- the visible application is one DeepFind window with no console window;
- backend readiness and failure states are understandable;
- closing the app leaves no DeepFind Java process and preserves committed local data;
- PDF/DOCX extraction, Lucene persistence, SQLite migration, filesystem watching, and platform actions work from the installed application;
- the installer does not request administrator privileges for the default per-user flow;
- uninstall behavior is explicit and does not silently delete the user's index;
- the packaged-runtime network audit still observes only the required loopback behavior.
