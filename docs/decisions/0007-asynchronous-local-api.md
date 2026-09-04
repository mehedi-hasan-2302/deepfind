# 0007 — Asynchronous loopback indexing API

## Context

Filesystem scans can run for minutes or hours. Running discovery and Lucene writes on an HTTP request thread would make the local client appear frozen and prevent honest progress reporting. Multiple concurrent scan writers would also make job state and resource usage harder to reason about.

## Decision

Expose indexing as an asynchronous job through `POST /api/index/start` and observe it with `GET /api/index/status`. A single application-owned daemon executor permits one active job; a second start receives a stable conflict error. Search remains available through `GET /api/search` while indexing uses Lucene's near-real-time reader refresh.

Spring owns the Lucene lifecycle. Metadata is stored under `${user.home}/.deepfind/index` by default, with `DEEPFIND_DATA_DIRECTORY` as the explicit parent-directory override. Test contexts always override storage into the operating-system temporary directory.

All API validation and operational failures use a stable `{code, message, details}` envelope. Responses may include selected/indexed paths because the local user needs them, but application code does not log paths or queries.

## Alternatives considered

- Synchronous scan endpoint: rejected because scan duration is unbounded and would occupy request threads.
- One worker per indexed root: deferred until a bounded multi-root scheduling policy and resource measurements exist.
- Persist job state immediately: deferred to the SQLite persistence phase; current job state is explicitly process-local.
- Enable cross-origin requests: rejected because the desktop/local client does not require broad browser origins and filesystem APIs should not gain unnecessary exposure.

## Consequences

The frontend must poll status in the current MVP. Pause, stop, recovery, and persisted roots remain future modules. Graceful shutdown waits briefly for the worker before interruption, and dependency-based Spring destruction closes the worker before Lucene.
