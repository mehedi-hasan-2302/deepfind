# 0015 — Apply filesystem events through a bounded serial index session

## Context

The recursive watcher provides low-latency hints, but applying them directly on its native event loop would make extraction and Lucene commits block directory registration and event collection. An unbounded asynchronous executor could hide temporary slowness by consuming arbitrary memory, while parallel event handling could reorder create, modify, and delete operations for the same path. Directory renames also require deleting every old descendant without deleting similarly prefixed siblings.

## Decision

Create one `IncrementalIndexingSession` per watched root. Accept normalized events into a configurable fixed-capacity queue and block the producer when it is full. Process events on one daemon worker to preserve accepted order. Drain available bursts, coalesce repeated same-path events while preserving a create followed by a redundant modify as create, and commit Lucene once after the burst's direct mutations.

For create and modify, re-read metadata with `NOFOLLOW_LINKS`, upsert metadata first, and re-extract regular-file content before replacing the same Lucene document. Recursively index a newly created directory because a populated directory may have been moved into the root before registration completed. If an entry disappears during the read, treat the event idempotently as deletion.

Add an exact-path plus separator-delimited descendant deletion operation to Lucene. Model rename using the portable watcher behavior of delete-old followed by create-new rather than trying to infer identity from timing.

Mark the session as requiring reconciliation after overflow, watcher failure, out-of-root input, unreadable metadata, processing failure, or forced shutdown. Ignore excluded events. Drain accepted work during bounded graceful shutdown and expose an idle wait only for lifecycle coordination and tests.

## Alternatives considered

- Apply mutations directly on the watcher thread: rejected because document extraction would increase native watcher overflow risk.
- Use multiple event workers: rejected because path mutations could complete out of order and resurrect deleted or stale documents.
- Use an unbounded executor queue: rejected because event bursts could consume arbitrary memory.
- Infer rename pairs from timestamps: rejected because native event ordering and identity information are not reliable across platforms.
- Index only the newly created directory entry: rejected because populated trees moved into the root could leave their existing children absent from search.

## Consequences

Incremental mutations are idempotent, ordered, memory-bounded, content-aware, and durable after their burst commit. Backpressure can slow the native watcher, which may cause an explicit overflow under extreme activity; reconciliation is the deliberate repair path. Recursive directory creation can be more expensive than a single-file update, but it closes the moved-tree race. This module does not yet own watcher startup or schedule reconciliation, so automatic freshness begins when the lifecycle coordinator is connected.
