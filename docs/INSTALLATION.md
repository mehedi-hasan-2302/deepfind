# Windows installation and build guide

DeepFind's Windows package is an **unsigned 0.1.0 preview**, not a signed production release. The setup executable is ready at `artifacts/DeepFind-Setup.exe`. See [the verification record and SHA-256](WINDOWS_PREVIEW_VERIFICATION.md) for completed checks and remaining limitations.

## Install and run

1. Use the verified `artifacts/DeepFind-Setup.exe` produced by the release build. Do not copy the internal `DeepFind.exe` alone: it needs its adjacent backend JAR and runtime files.
2. Run the setup executable and follow the per-user installation prompts. A publisher/reputation warning is possible because the preview is unsigned; verify the file's source and recorded SHA-256 before deciding whether to run it.
3. Start **DeepFind** from its shortcut. It starts its own Java process and local server; no terminal, Java installation, Node, or Rust is needed.
4. Enter the absolute path of a folder you want to search and start indexing. Native folder browsing and OCR are not included in this preview.
5. Search by filename or document text. Closing the window waits for local cleanup, normally briefly and at most 45 seconds before ending the owned backend.

The installer carries an offline WebView2 installer. Existing compatible WebView2 installations can be reused. A clean Windows machine without WebView2 still needs separate acceptance testing; the existing machine's test does not establish that case.

## Data and uninstall

Data defaults to `%USERPROFILE%\.deepfind`, independent of the installation directory. `DEEPFIND_DATA_DIRECTORY` overrides this for development and isolated tests. Do not run a development backend and the desktop app against the same index simultaneously.

Use Windows' installed-apps list or the installed uninstaller to remove the application. The installer does not manage `.deepfind` or an overridden index directory, so these remain after uninstall. The uninstaller's optional application-data checkbox concerns Tauri/WebView data under the application identifier, not the separate search index. Source documents are never part of the installed application and must not be deleted by uninstalling DeepFind.

There is no DeepFind automatic updater. WebView2 Evergreen is serviced separately by Microsoft. DeepFind configures an application-only rejecting HTTP/HTTPS proxy for WebView2; implicit loopback bypass allows the local server while other proxied requests are rejected without being read or forwarded. This is not an OS firewall or a guarantee about every WebView2 service/protocol. The sampled network audit covers DeepFind's process tree, not the system-wide Microsoft updater. See [Microsoft's WebView2 privacy guidance](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/data-privacy).

## How the project becomes an executable

1. **Compile the interface.** TypeScript/React source becomes static HTML, JavaScript, and CSS through Vite. These are files the embedded browser can display without a development server.
2. **Package the backend.** Maven's `desktop` profile puts those frontend files into the executable Spring Boot JAR together with the Java application and its dependencies.
3. **Bundle Java.** `jlink` creates a private Java 21 runtime. The broad Java SE module set deliberately keeps parser and service-loader support; runtime smoke tests exercise PDF, DOCX, SQLite, Lucene, and watching.
4. **Compile the native launcher.** Rust/Tauri becomes `DeepFind.exe`. It starts only the bundled Java executable, receives its selected port through a private pipe, checks health, and navigates the window to that exact loopback origin. Windows paths are simplified before passing the JAR to Java.
5. **Own the lifecycle.** A private stdin pipe requests graceful shutdown. A Windows job object prevents an orphaned Java process after a shell crash. User-opened editors/Explorer are allowed to outlive DeepFind. A second launch focuses the existing instance.
6. **Assemble the installer.** NSIS combines the native launcher, JAR, private Java runtime, icons, third-party notices, and offline WebView2 installation support into one setup `.exe`. The installer copies files and creates shortcuts/uninstall registration; it does not turn all components into a single self-contained executable file.
7. **Test the installed product.** Build success alone is insufficient. Test launch, indexing/search, restart, shutdown, duplicate launch, failure recovery, network behavior, and uninstall. A clean offline Windows VM is a separate release acceptance gate. Signing is another release step that requires the owner's publisher certificate.

## Rebuild from source

Build prerequisites: Java 21 JDK with `jlink`, Node 22/npm, Rust MSVC, and Microsoft C++ Build Tools/Windows SDK. These are developer tools, not end-user requirements.

From the repository root in PowerShell:

```powershell
.\scripts\build-desktop.ps1
```

The script verifies the frontend and backend, builds the private runtime, runs representative runtime smoke tests, collects notices, tests the Rust supervisor, and builds/copies the installer. Rust tests use the release profile to avoid the debug PDB filesystem failure observed on this build host. Generated files live under ignored `backend/target`, `frontend/dist`, `desktop/src-tauri/resources`, `desktop/src-tauri/target`, and `artifacts` directories; do not commit them.

Useful focused checks:

```powershell
.\scripts\test-desktop-runtime.ps1
.\scripts\audit-desktop-network.ps1 -AppProcessId <running-DeepFind-process-id>
Get-FileHash .\artifacts\DeepFind-Setup.exe -Algorithm SHA256
```

Smoke tests create small local fixtures and retain their isolated state under `artifacts/runtime-smoke-*` for inspection. They never select a user's real document directory automatically.
