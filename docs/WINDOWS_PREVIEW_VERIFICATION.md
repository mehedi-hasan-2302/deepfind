# Windows preview verification — 2026-09-12

## Deliverable

Unsigned DeepFind 0.1.0 Windows x64 preview: `artifacts/DeepFind-Setup.exe`.

- Size: 345,988,295 bytes (about 330 MiB).
- SHA-256: `A74FF3B059744A8B805AD8344D24CD539FAEA2E7276EE36A2E548CB7D9ACBADB`.
- Includes the native launcher, compiled React/Spring Boot application, private Java 21 runtime, third-party notices, and offline WebView2 installation support.
- The installer and build outputs are intentionally ignored by Git. This hash identifies this exact local artifact, not future rebuilds.

## Completed checks

| Check | Observed result |
| --- | --- |
| Backend verification | 107 tests discovered, 104 passed, 3 host-dependent symlink tests skipped; no failures/errors. |
| Frontend verification | 14 tests, lint, TypeScript, and production build passed. |
| Rust supervisor tests | Four release-profile tests passed, including Windows path normalization, handshake/navigation validation, and the rejecting proxy. |
| Private runtime smoke | Text, PDF, DOCX, SQLite/Lucene persistence, live watching, UI/CSP, and graceful pipe shutdown passed. |
| NSIS build | Initial final packaging attempt timed out; retrying `npm run build -- --bundles nsis` succeeded. No earlier installer was substituted. |
| Installation | Silent per-user install returned 0 in `artifacts/install-smoke-20260912/DeepFind Preview`, exercising a path containing spaces. Registration and required runtime/JAR/notices files were present. |
| Launcher provenance | Installed executable differed from the release executable by only the expected three-byte Tauri bundle marker (`NSS` versus `UNK`). Installed/backend-source JAR SHA-256 values matched exactly. |
| Developer-tool independence | Installed app started with only Windows directories in its PATH and no JAVA_HOME. Its Java child came from the installation's private runtime, not the system JDK. This is not a clean-machine test. |
| Native window | The installed React interface and updated preview wording were read through Windows accessibility. No console window appeared in the window inventory. |
| Installed indexing/search | Isolated text, DOCX, and PDF fixtures were found by unique content canaries; indexing completed with zero failures. A newly created text file became searchable through the watcher. |
| Duplicate launch | Second shell exited 0, with the original Java PID unchanged and no second backend. |
| Normal shutdown | Window-close request returned shell exit 0 and removed the owned Java process. |
| Restart | All four content canaries and the selected root persisted across restart. |
| Backend crash | Terminating only the test Java child displayed the local recovery page. The recovery window closed with exit 0. Reopening recovered the committed index. |
| Shell crash | Terminating only the test shell removed its Java child through the Windows job object. |
| Open/reveal | Installed API actions returned OPENED/REVEALED for the sample text file. Notepad and Explorer windows appeared and survived shell termination, as intended. |
| Network sampling | Two 20-sample audits of the installed shell, Java, and discovered WebView2 descendants observed only loopback TCP and no UDP, before and after indexing/search. WebView2 used the application-scoped proxy. |
| Uninstall | Uninstaller returned 0; its asynchronous worker removed the test application and uninstall registration. The separate SQLite/index directory and all four sample source files remained. |

The temporary installation was removed after testing; the setup executable and isolated fixtures/state remain available. The default user index was not used. No system proxy, firewall, or privacy settings were changed.

## Limits and remaining release gates

- This is an installable preview, not completion of the clean-machine production acceptance gate in `PACKAGING.md`.
- Test on clean Windows without Java, Node, Rust, WebView2, or internet to verify first-time offline WebView2 installation. This host already had WebView2 152.0.4191.66.
- Publisher signing and reputation verification require the owner's signing identity/certificate. This artifact is unsigned.
- Windows screenshot capture failed with `SetIsBorderRequired` / `0x80004002`. An editable-value automation attempt failed with UIA `0x80070057`; it was not counted as a successful UI search. Accessibility page reading, window lifecycle, installed API behavior, and frontend interaction tests provided the recorded evidence. Full visual/manual end-to-end UI acceptance remains outstanding.
- Network sampling is not packet-content inspection or proof of all future behavior. The rejecting proxy covers requests routed through it, not all native services or protocols. Microsoft runtime servicing is separate; see `PRIVACY.md` and ADR 0031.
- The preview requires a typed folder path and has no OCR. The index/database are not encrypted; existing parser/watch/path-race limitations remain documented in `PROGRESS.md`.

See [the installation and build guide](INSTALLATION.md) for installation, data locations, and the seven stages that turn the source project into a setup executable.
