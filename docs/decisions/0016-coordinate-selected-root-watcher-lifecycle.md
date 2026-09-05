# 0016 — Coordinate one active watcher with full indexing jobs

## Context

The native watcher and bounded incremental index session work independently, but the application needs one owner for startup restoration, selected-root changes, full indexing, failures, and shutdown. Leaving multiple sessions active would duplicate updates and retain obsolete roots. Allowing incremental mutations to race a full traversal could also let an older scan observation overwrite a newer watcher observation.

## Decision

Add `IndexWatchCoordinator` as the owner of one active pair: a `FileWatchSession` and its upstream `IncrementalIndexingSession`. Restore the persisted selected root after Spring constructs the service. An absent, unreadable, file, or symbolic-link root produces a safe failed watch status without failing application startup.

Before a full indexing job, close the native watcher first and then drain and close its incremental session. Resume a fresh pair for the job root in a `finally` block after either completion or failure. Switching roots uses the same close-old-before-open-new ordering. On application shutdown, stop the native producer before the incremental consumer so accepted events can drain before Lucene closes.

Keep watch lifecycle behind a narrow `IndexWatchLifecycle` contract so indexing jobs do not know native watcher details. Retain internal states for stopped, watching, reconciliation required, and failed; exposing those states through the API and UI belongs to watcher failure recovery work.

Accept a temporary scan-time observation gap. Applying events concurrently with the full traversal was rejected because stale scan reads could overwrite newer event updates. The next reconciliation module must run after or around this boundary so a change made after traversal of its path cannot remain missed.

## Alternatives considered

- Leave startup and shutdown to controllers: rejected because lifecycle correctness must not depend on a browser session or HTTP request.
- Keep old and new root watchers active: rejected because the MVP has one selected active root and multiple sessions create ambiguous ownership.
- Apply watcher events concurrently with full indexing: rejected because operation completion order would not represent filesystem order.
- Fail application startup when the persisted root is unavailable: rejected because removable and renamed folders must not make existing committed search data unusable.

## Consequences

After a successful scan, ordinary create, modify, rename, and delete activity updates search results automatically. The persisted root resumes watching after restart, root switches release old resources, and failed scans do not leave watching permanently disabled. Correct shutdown ordering protects the Lucene lifecycle. Full correctness still depends on the upcoming reconciliation mechanism because events can be missed during scans or native overflow.
