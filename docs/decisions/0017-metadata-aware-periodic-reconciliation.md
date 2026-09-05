# 0017 — Reconcile index state from filesystem metadata

## Context

Native filesystem notifications are advisory: providers may coalesce or overflow events, and watcher coordination deliberately pauses observation while a full traversal owns index mutation. Re-running full content extraction after every uncertainty would repair the index but would repeatedly parse unchanged documents and waste local CPU and I/O.

## Decision

Reconcile the selected root against a read-only Lucene metadata snapshot. Compare kind, byte size, and millisecond-precision creation and modification timestamps because that is the precision persisted by the index. Preserve matching documents, and process only new, changed, or regular-file entries whose content extraction was never attempted. After discovery and bounded content work finish, remove scoped index entries only when the source path is excluded or `Files.notExists` proves it absent using no-follow semantics. Preserve entries when existence is unknown.

Run reconciliation through the existing single indexing-job executor and durable scan-history lifecycle. Pause and drain the watcher before reconciliation, then resume it in the same completion path used by full indexing. Poll watcher state every 30 seconds so explicit uncertainty requests prompt repair when the worker is idle; otherwise start reconciliation at most every 15 minutes. Keep these intervals configurable.

## Alternatives considered

- Fully re-extract every document periodically: rejected because unchanged PDF and Office parsing is expensive and unnecessary.
- Trust only native events: rejected because overflow and deliberate pause windows cannot provide complete event histories.
- Delete every index path not observed in one traversal: rejected because permission and transient I/O failures could turn uncertainty into data loss.
- Run reconciliation concurrently with full indexing: rejected because both mutate the same path keys and completion order could publish stale state.

## Consequences

Missed creates, modifications, interrupted content attempts, and proven deletions are repaired automatically without routinely re-parsing unchanged content. Reconciliation still traverses filesystem metadata, so very large roots incur periodic I/O. A change during the reconciliation pause can require the next scheduled pass; correctness is eventual rather than an atomic filesystem snapshot. Manual refresh and user-visible watcher/reconciliation state remain separate interface work.
