# 0005 — Filesystem discovery policy

## Context

DeepFind must traverse large, private directory trees without cycles, unbounded memory growth, or a single unreadable entry terminating an indexing job.

## Decision

Use Java NIO `walkFileTree` without following symbolic links. Emit immutable metadata and progress incrementally through an observer instead of returning an in-memory collection. Index a symbolic link as an entry but do not recurse into a linked directory. Record access, disappearance, security, and I/O failures with bounded user-safe messages, then continue when the visitor permits it.

Apply a small visible default segment exclusion set: `.git`, `node_modules`, `target`, `build`, `dist`, and `.gradle`. A selected root itself is never rejected merely because its name matches a default segment. Explicit excluded paths include their descendants.

Normalize absolute paths lexically without resolving symlink targets. Lowercase search keys only on Windows; preserve case on platforms where distinct paths may differ only by case.

## Alternatives considered

- `Files.walk` returning a stream: rejected because visitor callbacks give clearer failure recovery and subtree skipping.
- Follow symbolic links with a visited set: rejected as an unsafe default because junctions, cycles, and unexpectedly large linked trees are common.
- Accumulate every discovered file before indexing: rejected because millions of paths would create unbounded memory pressure and delay usefulness.
- Aggressive system-directory exclusions: rejected because they can silently hide documents users intentionally selected.

## Consequences

Consumers must process observer callbacks promptly or introduce a bounded queue with backpressure. Progress totals count accepted entries, skipped exclusion roots, and failures; exact total size remains unknown during discovery. Users may opt into broader symlink behavior in a future version, but reconciliation must preserve cycle safety.
