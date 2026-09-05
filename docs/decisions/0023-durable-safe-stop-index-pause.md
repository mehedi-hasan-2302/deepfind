# 0023 — Pause indexing as a durable safe stop

## Context

Users need to yield disk and parser work during a long scan. Freezing a `Files.walkFileTree` call in memory would leave extraction workers and watcher ownership ambiguous, would not survive application restart, and could resume from an unsafe position after earlier paths changed.

## Decision

Model pause as a controlled stop with durable `PAUSING` and `PAUSED` states. A pause request changes the running SQLite job to `PAUSING` before signaling the worker. At the next discovery progress boundary, stop traversal with an internal control-flow signal, drain bounded in-flight extraction, commit completed Lucene mutations, and finalize the same history row as `PAUSED`. Resume native watching after the stopped job releases ownership.

Resume validates the persisted root and schedules a new changed-only reconciliation job with a new identifier. Reconciliation compares current filesystem metadata with the committed Lucene snapshot, reprocesses changed or incomplete entries, and prunes only proven missing paths. It does not trust the paused job's displayed current path as a durable cursor.

Treat `RUNNING`, `PAUSING`, and `PAUSED` as mutually exclusive ownership of the single job lane. Periodic repair, manual refresh, root changes, and additional starts cannot overlap them. On startup, reclassify abandoned `RUNNING` or `PAUSING` jobs as `INTERRUPTED`; retain a completed `PAUSED` row as resumable.

## Alternatives considered

- Block the indexing thread on a condition and continue the same traversal: rejected because it retains process resources, complicates shutdown, and cannot be restored safely after restart.
- Interrupt the executor immediately: rejected because interruption can cut through parser/index operations and turn an intentional pause into a failure.
- Mark the job paused without stopping work: rejected because the state would be misleading.
- Resume from `current_path`: rejected because filesystem traversal order is not a durable cursor and changes before that path could be missed.

## Consequences

Pause can take up to the current safe boundary and bounded extraction drain, but completed work is durable and the worker becomes idle afterward. Resume may revisit the tree, yet metadata-aware reconciliation avoids re-extracting unchanged completed files and repairs changes made during the pause. A crash before `PAUSED` is durably finalized remains honestly interrupted.
