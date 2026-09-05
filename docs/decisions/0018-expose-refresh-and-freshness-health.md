# 0018 — Expose explicit refresh and freshness health

## Context

Automatic watching and scheduled reconciliation keep the index fresh, but hiding their state leaves users unable to distinguish active tracking, an intentional indexing pause, uncertainty awaiting repair, and an unavailable folder. Users also need an immediate repair action without paying for a full content rebuild.

## Decision

Expose `GET /api/index/watch-status` as a narrow read-only contract containing the selected root, stable watcher state, and safe message. Expose `POST /api/index/refresh` as an asynchronous request for metadata-aware reconciliation of the persisted selected root. Route manual refresh through the same single-job executor, durable scan history, watcher pause/resume lifecycle, and concurrent-job rejection used by automatic work.

Poll watcher health separately from indexing status in the React client. Display explicit live-update states, explain that tracking pauses during an indexing job, and enable refresh only when the path field still represents the persisted selected root. Keep **Start indexing** as the action for selecting or fully rescanning a changed path.

## Alternatives considered

- Fold watcher state into every indexing-status response: rejected because job lifecycle and ongoing freshness are independent concerns with different polling needs.
- Make refresh synchronous: rejected because filesystem traversal and document extraction can exceed an HTTP request lifetime.
- Let refresh accept an arbitrary path: rejected because that duplicates root-selection semantics and could make the displayed folder differ from the repaired folder.
- Expose internal exception details: rejected because the loopback API still requires stable, content-free errors.

## Consequences

Users can see whether live updates are healthy and can request immediate changed-only repair. Refresh remains searchable and non-overlapping while it runs, and its outcome appears in existing scan history. A native folder picker, richer job-type labels, pause/resume controls, and detailed diagnostics belong to later indexing UX and desktop phases.
