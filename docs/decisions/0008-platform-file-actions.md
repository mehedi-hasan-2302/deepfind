# 0008 — Validate and isolate platform file actions

## Context

Search results need Open and Show in Folder actions, but invoking operating-system applications from path input creates a sensitive boundary. Shell command interpolation would make filenames containing command characters unsafe, and platform-specific behavior does not belong in controllers or React components.

## Decision

Place open/reveal behavior behind the backend `FileActions` interface. The platform implementation accepts only existing absolute paths, normalizes them, and constructs process arguments as discrete values through `ProcessBuilder`; it never builds a shell command string. Windows uses Explorer, macOS uses `open`, and Linux uses `xdg-open`. Unsupported platforms and process failures return stable, non-sensitive API errors.

Expose the actions through narrow loopback-only `POST /api/files/open` and `POST /api/files/reveal` endpoints. Keep full-path and containing-folder clipboard operations in the frontend because they do not require operating-system process access. Each result card reports action success or failure without changing the indexed file.

## Alternatives considered

- Run shell commands from the frontend: rejected because the browser has no appropriate process-launch capability and shell interpolation is unsafe.
- Use one generic execute endpoint: rejected because it would expose a broader local process surface than the product needs.
- Send clipboard writes through the backend: rejected because the browser Clipboard API already provides the narrower capability.
- Depend directly on Java AWT Desktop: deferred because support varies in headless and packaged runtime configurations; explicit platform commands are easier to test and package deliberately.

## Consequences

Platform command construction can be unit-tested without opening visible applications. Packaging must include or document the expected platform launchers, and visible end-to-end action checks remain part of desktop-shell acceptance testing. A disappeared result produces a clear error instead of attempting to launch a stale path.
