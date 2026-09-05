# Architecture

## Status

Phases 1–3 provide an end-to-end local search slice with durable index, root configuration, scan history, and interrupted-run detection. Phase 4 now has a recursive filesystem event boundary. Filesystem discovery, persistent Lucene filename/path/content indexing, bounded document extraction, highlighted content snippets, a loopback API, a React interface, guarded platform file actions, and low-level change detection are implemented. Applying change events to Lucene, reconciliation, and desktop packaging remain pending.

## Components

- `frontend`: React and TypeScript interface served by Vite during development.
- `backend`: Java 21 Spring Boot modular monolith exposing a local HTTP API.
- Infrastructure adapters: Lucene for local full-text search, Apache Tika for bounded extraction, SQLite for structured application state, and platform adapters for open/reveal actions.
- Future desktop shell: responsible for starting the backend, waiting for health, hosting the UI, and shutting down cleanly.

## Dependency direction

```text
API/UI -> application services -> domain contracts -> infrastructure adapters
```

Controllers will not operate Lucene readers, Tika parsers, databases, or filesystem walkers directly.

## Filesystem discovery

Discovery uses Java NIO `walkFileTree` and emits immutable metadata, progress snapshots, and bounded failure descriptions through an observer. It does not retain the discovered tree in memory. Directory symlinks are indexed as link entries but are not followed. A small default exclusion set removes common generated trees; explicit exclusions can target an absolute subtree. See ADR 0005.

## Filesystem change detection

`RecursiveFileWatcher` registers the selected root and each eligible real directory with Java NIO `WatchService`. It applies the same exclusion policy as discovery, never follows directory symbolic links, and registers eligible directories created after startup before publishing their create event. Events contain normalized absolute paths and one of `CREATED`, `MODIFIED`, `DELETED`, or `OVERFLOW`.

Each watch session owns one daemon thread and invokes its observer synchronously, so the application layer does not add an unbounded event queue. Native providers may coalesce, duplicate, reorder, or overflow events; `OVERFLOW` identifies the affected directory and is a repair signal, not a fabricated per-file change. The watcher reports categorized, content-free failures, stops when its root becomes unavailable, and has idempotent bounded shutdown. Index mutation, event coalescing, root lifecycle ownership, and reconciliation are intentionally outside this low-level boundary. See ADR 0014.

## Data flow

The indexing pipeline is discovery → metadata upsert → bounded extraction queue/workers → same-key content update → commit/progress completion. Lucene's near-real-time reader can expose metadata upserts before slower content extraction and the final durable commit. A full document replacement keeps one entry per normalized path because Lucene does not perform partial field updates.

The search pipeline is React's debounced query state → relative `/api/search` request → normalization → Lucene query → filename-first ranking → explanatory match category → bounded plain-text snippet with highlight offsets → API DTO → safely rendered result card. Filters remain a future extension.

During development, Vite proxies relative `/api` traffic to `127.0.0.1:8080`. This keeps browser calls same-origin without widening the backend's network or CORS boundary. The frontend polls indexing status only while a job is running and aborts obsolete search requests when the query changes.

## Concurrency

Discovery, filesystem watching, extraction, index writing, and search use separate execution boundaries. Watch observers execute on their session's single daemon thread without another queue. Content-indexing workers use a fixed pool and bounded queue; when that queue fills, the discovery caller performs extraction work to apply backpressure instead of accumulating paths. The extractor has its own fixed parser pool and bounded queue so parser capacity remains independently enforced.

## Content extraction

The `ContentExtractor` contract isolates callers from Apache Tika. A conservative extension allowlist is checked first, then Tika detection must confirm a compatible media type before parsing. Initial formats are plain text, Markdown, common source/configuration files, PDF, and DOCX. Embedded documents are disabled.

Files above the configured byte limit are rejected before parsing. A bounded executor limits concurrency and queued work, Tika's write limit caps extracted characters, and each request has a deadline. Outcomes are explicit: `SUCCESS`, `UNSUPPORTED`, `SKIPPED_TOO_LARGE`, `PERMISSION_DENIED`, `PARSE_ERROR`, or `TIMEOUT`. Failures do not expose parser exception details or file contents.

The current timeout uses interruption of an in-process parser worker. It bounds how long the caller waits but is cooperative rather than hard process isolation. A parser that ignores interruption could occupy a worker until it returns; risky formats may move to Tika's process-isolated facilities in a later hardening step. See ADR 0009.

## Storage roles

- Lucene: authoritative full-text index for filename, path, metadata, and extracted content.
- SQLite: indexed roots, settings, scan jobs, progress checkpoints, and categorized scan failures. Exclusions remain a future extension.
- Filesystem: read-only source data. DeepFind does not modify indexed files.

## Security boundary

The backend binds to `127.0.0.1`, never `0.0.0.0`, by default. The initial health API exposes no file data. Future filesystem actions must validate input and avoid arbitrary content-read endpoints.

## Runtime configuration and API

Spring owns one Lucene index lifecycle and closes it on shutdown. The index defaults to `${user.home}/.deepfind/index`; `DEEPFIND_DATA_DIRECTORY` overrides the shared parent data directory for packaging and tests. A SQLite database at `<data-directory>/deepfind.db` stores normalized indexed-root records and generic application settings. Flyway migrates it before persistence-backed services initialize. SQLite foreign keys, a five-second busy timeout, write-ahead logging, and normal synchronous mode are configured on each connection. Startup fails on invalid migrations or database corruption instead of deleting state.

Selecting a valid indexing root transactionally upserts its normalized record and the `last_selected_root` setting before the background job starts. The job is then inserted as `RUNNING`. Progress is checkpointed every 250 discovered entries or two seconds, whichever occurs first, avoiding a database write for every file. Discovery failures are stored separately with stable categories and bounded messages. Completion stores final counters and updates `last_indexed_at`; terminal failures are also persisted.

On startup, any abandoned `RUNNING` row becomes `INTERRUPTED` rather than completed or deleted. The latest interruption is exposed as the current status with a restart-to-reconcile message, while committed Lucene results remain searchable. A bounded `GET /api/index/history` endpoint exposes recent history through API DTOs. The existing frontend restores the root and allows a new full scan to reconcile it; true mid-tree continuation is not claimed. See ADRs 0012 and 0013.

One daemon worker accepts at most one indexing job at a time, while search uses Lucene's independently refreshed readers. The local API exposes indexing start/status and filename, path, and content search. Requests are validated and failures use stable error codes without Java stack traces.

## Index model

Lucene schema version 3 indexes filenames, paths, and extracted content with a delimiter-aware lowercase analyzer. A normalized absolute-path key is exact and unique for upsert/delete. Display metadata, extraction status/reason, and an extraction-bounded snippet source are stored locally. Size and timestamps additionally use points for range filters and numeric doc values for sorting. Exact filename, filename prefix, and general filename clauses outrank path and content clauses. Content-only results receive whitespace-normalized excerpts with at most 240 body characters and ordered UTF-16 highlight ranges. `SearcherManager` provides near-real-time visibility, while explicit commits provide restart durability. Schema version lives in commit metadata and incompatible versions fail explicitly; earlier indexes require a rebuild. See ADRs 0006, 0010, and 0011.

## Platform integration

Open and reveal actions live behind the `FileActions` platform abstraction and narrow loopback POST endpoints. Requests must identify an existing absolute path. The implementation passes paths as discrete process arguments without shell interpolation: Explorer on Windows, `open` on macOS, and `xdg-open` on Linux. Clipboard operations stay in the browser. See ADR 0008.
