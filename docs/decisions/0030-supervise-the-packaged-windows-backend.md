# 0030 — Supervise the packaged Windows backend

## Context

ADR 0029 selected Tauri and a bundled Java backend but left the handshake and process ownership details open. Implementation also found that Tauri's canonical Windows verbatim path spelling can prevent Java's Boot JAR launcher from loading its entry class.

## Decision

Normalize representable Windows resource paths with `dunce::simplified` before launching the bundled Java executable/JAR. Preserve argument boundaries through `Command`, never a command shell. Remove inherited JVM-option injection variables from this child.

The backend emits one `DEEPFIND_READY <port>` stdout event after Spring starts. Rust drains stdout with bounded line storage, discards raw diagnostics, validates the port and local health, and permits navigation only to the exact backend origin or named bundled status pages. Backend startup has a 120-second deadline. Startup status URLs are explicit because the newly created WebView can still report `about:blank`.

Keep inherited stdin open while the shell owns Java; pipe closure/EOF requests Spring context shutdown. Normal close waits up to 45 seconds, then kills only the retained child if needed. A Windows job with kill-on-close contains the Java process after shell failure; silent descendant breakaway ensures Explorer/editors opened by the user are not killed with DeepFind. This is lifecycle ownership, not parser security isolation.

Use Tauri's single-instance plugin before creating any backend/window. No generic shell, filesystem, or process capability is granted to the renderer. User data remains separate from installed resources.

## Consequences

The installed application needs no system Java or developer server. Source documents, private backend diagnostics, and queries are not added to installer metadata. Tests cover readiness parsing, exact-origin navigation, and Windows path spelling; packaged runtime and installed-process tests remain essential. Same-user local processes are still inside the documented trust boundary, and abnormal termination can lose uncommitted work. A clean-machine offline test and publisher signing remain separate release gates.
