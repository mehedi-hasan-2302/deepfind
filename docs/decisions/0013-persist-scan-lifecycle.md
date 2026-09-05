# 0013 — Persist scan lifecycle and classify interrupted runs

## Context

Lucene commits preserve searchable data across restarts, but process-local indexing status cannot distinguish a cleanly completed scan from a process that stopped mid-scan. Phase 3 requires durable history, failure records, and honest restart behavior without reducing indexing throughput or pretending that arbitrary filesystem traversal can resume from one path safely.

## Decision

Add migrated `scan_jobs` and `scan_failures` tables. A job records its root, lifecycle state, current checkpoint path, discovery/index counters, safe terminal message, and start/finish timestamps. Failures retain their stable category, bounded user-facing message, path, and time.

Insert the job before submitting background work. Checkpoint progress after 250 additional discovered entries or two elapsed seconds, whichever occurs first. Persist individual discovery failures because they are exceptional rather than the hot path. Store exact final counters on completion or failure.

At application startup, atomically reclassify every abandoned `RUNNING` job as `INTERRUPTED`. Show the newest recovered job as interrupted and invite the user to start indexing again. The new scan is a full reconciliation of the root; DeepFind does not resume from the stored current path because entries before that path may have changed while the process was stopped. Keep committed Lucene documents usable throughout.

Expose up to 100 recent jobs through a read-only loopback history endpoint using API DTOs rather than persistence records.

## Alternatives considered

- Write progress for every discovered entry: rejected because synchronous SQLite writes would unnecessarily limit scan throughput.
- Store only terminal jobs: rejected because a crash would remain indistinguishable from a job that never existed.
- Resume traversal after the last path: rejected because filesystem order is not a durable cursor and would miss changes earlier in the tree.
- Delete incomplete history at startup: rejected because it hides recovery state and destroys useful diagnostics.

## Consequences

Crashes and forced exits are detectable, recent scan outcomes survive restarts, and the UI gives an honest recovery action. At most one checkpoint interval of counters can be lost during an abrupt stop, while individual failures already written remain available. SQLite stores additional sensitive paths and failure descriptions, so the local database must be protected like the Lucene index. Exact continuation remains future work only if a safe durable work-queue design justifies its complexity.
