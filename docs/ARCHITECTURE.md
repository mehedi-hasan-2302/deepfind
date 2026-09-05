# Architecture

## Status

Phases 1–4 provide an end-to-end local search slice with durable index, root configuration, scan history, interrupted-run detection, automatic selected-root watching, bounded incremental Lucene updates, scheduled reconciliation, manual refresh, and visible freshness health. Filesystem discovery, persistent Lucene filename/path/content indexing, bounded document extraction, highlighted content snippets, a loopback API, a React interface, guarded platform file actions, event application, watcher lifecycle coordination, and automatic repair are implemented. Search-quality expansion, indexing UX, hardening, and desktop packaging remain.

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

`IncrementalIndexingService` opens one application session per root. Its fixed-capacity queue blocks the watcher producer when full, and its single daemon worker preserves accepted event order. Each drained burst keeps only the strongest latest event per normalized path, preserving create over a following redundant modify, and commits once after its direct mutations. Queue capacity defaults to 256 and graceful shutdown to 30 seconds.

Create and modify events re-read no-follow metadata; regular files are re-extracted and replace the complete Lucene document. A newly created directory is discovered recursively so a moved-in populated tree is not missed. Delete removes the exact normalized path and every true descendant without matching similarly prefixed sibling names. Rename therefore works as the native delete-old plus create-new pair. Overflow, watcher failure, unreadable metadata, processing failure, forced shutdown, and out-of-root input set an explicit reconciliation-required flag. Excluded events are ignored. See ADR 0015.

`IndexWatchCoordinator` owns the active native and incremental sessions. At startup it restores the persisted selected root without making an unavailable folder fatal to application startup. Selecting and fully indexing a root stops the native session first, drains its accepted incremental work, runs the full scan, and resumes watching in a `finally` path after success or failure. Switching roots closes the old pair before opening the new pair. Application shutdown closes the watcher before draining and closing the incremental session, which remains upstream of Lucene destruction.

Pausing during a full scan prevents unordered watcher mutations from racing the traversal, but it introduces a window in which a change can occur after its path was visited and before watching resumes. Scheduled reconciliation repairs this gap eventually. It also runs promptly after the watcher reports uncertainty, subject to the shared indexing worker becoming idle.

`MetadataReconciliationService` compares each discovered entry with a read-only Lucene metadata snapshot. It preserves unchanged documents, re-extracts only new, changed, or never-attempted regular files, and deletes scoped entries only when Java can prove the source path absent with no-follow semantics. Unknown or unreadable existence is preserved rather than guessed deleted. `ReconciliationScheduler` polls every 30 seconds, starts a repair immediately for reconciliation-required watcher state, and otherwise enforces a 15-minute cadence. Reconciliation uses the same single-job lane, durable scan history, and watcher pause/resume boundary as a full scan, so the two never overlap. See ADR 0017.

The local API exposes `POST /api/index/refresh` for an explicit changed-only repair and `GET /api/index/watch-status` for safe root, state, and message fields. Manual refresh rejects missing roots and overlapping jobs through stable API errors. The frontend polls freshness independently of indexing progress, explains temporary job-time pauses, and offers refresh only for the persisted selected root. See ADR 0018.

## Data flow

The full indexing pipeline is discovery → metadata upsert → bounded extraction queue/workers → same-key content update → commit/progress completion. The incremental pipeline is watcher event → bounded per-root queue → coalesced serial application → metadata/content replacement or subtree delete → burst commit. Reconciliation is metadata snapshot → discovery comparison → changed-only metadata/content replacement → proven-missing pruning → commit. Lucene's near-real-time reader can expose metadata upserts before slower content extraction and the final durable commit. A full document replacement keeps one entry per normalized path because Lucene does not perform partial field updates.

The search pipeline is React's debounced query state → relative `/api/search` request → safe quote-aware normalization → programmatic Lucene term/phrase query → filename-first ranking → explanatory match category → bounded plain-text snippet with highlight offsets → API DTO → safely rendered result card. Balanced double quotes create required phrase clauses across filename, path, and content; ordinary text outside quotes remains required, and an unmatched quote falls back to ordinary text. Filters remain a future extension.

During development, Vite proxies relative `/api` traffic to `127.0.0.1:8080`. This keeps browser calls same-origin without widening the backend's network or CORS boundary. The frontend polls indexing status only while a job is running and aborts obsolete search requests when the query changes.

## Concurrency

Discovery, filesystem watching, incremental application, extraction, index writing, and search use separate execution boundaries. The native watch observer hands events to a fixed-capacity application queue; a full queue blocks that observer and native overflow remains detectable. One incremental worker preserves mutation order. Content-indexing workers use a fixed pool and bounded queue; when that queue fills, the discovery caller performs extraction work to apply backpressure instead of accumulating paths. The extractor has its own fixed parser pool and bounded queue so parser capacity remains independently enforced.

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

Lucene schema version 3 indexes filenames, paths, and extracted content with a delimiter-aware lowercase analyzer. A normalized absolute-path key is exact and unique for upsert/delete. Display metadata, extraction status/reason, and an extraction-bounded snippet source are stored locally. Size and timestamps additionally use points for range filters and numeric doc values for sorting. Exact filename, filename prefix, general filename, and filename phrase clauses outrank path and content clauses; exact content phrases outrank general content relevance. Content results receive whitespace-normalized excerpts with at most 240 body characters and ordered UTF-16 highlight ranges. `SearcherManager` provides near-real-time visibility, while explicit commits provide restart durability. Schema version lives in commit metadata and incompatible versions fail explicitly; earlier indexes require a rebuild. See ADRs 0006, 0010, 0011, and 0019.

## Platform integration

Open and reveal actions live behind the `FileActions` platform abstraction and narrow loopback POST endpoints. Requests must identify an existing absolute path. The implementation passes paths as discrete process arguments without shell interpolation: Explorer on Windows, `open` on macOS, and `xdg-open` on Linux. Clipboard operations stay in the browser. See ADR 0008.
