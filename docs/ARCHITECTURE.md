# Architecture

## Status

Phases 1–7 provide an end-to-end local search slice with durable index, root configuration, scan history, interrupted-run detection, automatic selected-root watching, bounded incremental Lucene updates, scheduled reconciliation, manual refresh, visible freshness health, pause/resume, persisted per-root exclusions, phrase search, explicit filters, bounded fuzzy fallback, highlighting, pagination, and a reviewed local security/privacy boundary. Filesystem discovery, persistent Lucene filename/path/content indexing, bounded document extraction, a loopback API, a React interface, guarded platform file actions, event application, watcher lifecycle coordination, automatic repair, understandable indexing controls, privacy-safe diagnostics, adversarial parser safeguards, and a repeatable runtime-network audit are implemented. Desktop packaging, performance validation, and beta readiness remain.

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

Discovery uses Java NIO `walkFileTree` and emits immutable metadata, progress snapshots, and bounded failure descriptions through an observer. It does not retain the discovered tree in memory. Directory symlinks are indexed as link entries but are not followed. A small default exclusion set removes common generated trees. User exclusions are persisted per normalized root as validated root-relative paths and resolved to absolute subtrees only inside the backend. See ADRs 0005 and 0024.

## Filesystem change detection

`RecursiveFileWatcher` registers the selected root and each eligible real directory with Java NIO `WatchService`. It applies the same exclusion policy as discovery, never follows directory symbolic links, and registers eligible directories created after startup before publishing their create event. Events contain normalized absolute paths and one of `CREATED`, `MODIFIED`, `DELETED`, or `OVERFLOW`.

Each watch session owns one daemon thread and invokes its observer synchronously, so the application layer does not add an unbounded event queue. Native providers may coalesce, duplicate, reorder, or overflow events; `OVERFLOW` identifies the affected directory and is a repair signal, not a fabricated per-file change. The watcher reports categorized, content-free failures, stops when its root becomes unavailable, and has idempotent bounded shutdown. Index mutation, event coalescing, root lifecycle ownership, and reconciliation are intentionally outside this low-level boundary. See ADR 0014.

`IncrementalIndexingService` opens one application session per root. Its fixed-capacity queue blocks the watcher producer when full, and its single daemon worker preserves accepted event order. Each drained burst keeps only the strongest latest event per normalized path, preserving create over a following redundant modify, and commits once after its direct mutations. Queue capacity defaults to 256 and graceful shutdown to 30 seconds.

Create and modify events re-read no-follow metadata; regular files are re-extracted and replace the complete Lucene document. A newly created directory is discovered recursively so a moved-in populated tree is not missed. Delete removes the exact normalized path and every true descendant without matching similarly prefixed sibling names. Rename therefore works as the native delete-old plus create-new pair. Overflow, watcher failure, unreadable metadata, processing failure, forced shutdown, and out-of-root input set an explicit reconciliation-required flag. Excluded events are ignored. See ADR 0015.

`IndexWatchCoordinator` owns the active native and incremental sessions. At startup it restores the persisted selected root without making an unavailable folder fatal to application startup. Selecting and fully indexing a root stops the native session first, drains its accepted incremental work, runs the full scan, and resumes watching in a `finally` path after success or failure. Switching roots closes the old pair before opening the new pair. Application shutdown closes the watcher before draining and closing the incremental session, which remains upstream of Lucene destruction.

Suspending native watching during a full scan prevents unordered watcher mutations from racing the traversal, but it introduces a window in which a change can occur after its path was visited and before watching resumes. Scheduled reconciliation repairs this gap eventually. It also runs promptly after the watcher reports uncertainty, subject to the shared indexing worker becoming idle.

User-requested job pause is a controlled terminal boundary rather than a suspended Java traversal. The state advances `RUNNING` → `PAUSING` → `PAUSED`; discovery stops at the next progress boundary, bounded in-flight extraction drains, partial Lucene work commits, and native watching resumes. Resume creates a new reconciliation job and job ID so current filesystem state—not an obsolete traversal cursor—decides what remains. A paused job survives restart as paused, while a crash during `RUNNING` or `PAUSING` is recovered as interrupted. See ADR 0023.

`MetadataReconciliationService` compares each discovered entry with a read-only Lucene metadata snapshot. It preserves unchanged documents, re-extracts only new, changed, or never-attempted regular files, and deletes scoped entries only when Java can prove the source path absent with no-follow semantics. Unknown or unreadable existence is preserved rather than guessed deleted. `ReconciliationScheduler` polls every 30 seconds, starts a repair immediately for reconciliation-required watcher state, and otherwise enforces a 15-minute cadence. Reconciliation uses the same single-job lane, durable scan history, and watcher pause/resume boundary as a full scan, so the two never overlap. See ADR 0017.

`RootExclusionService` stores at most 100 validated relative paths per selected root in generic SQLite application settings. Both slash styles are normalized, duplicates collapse by normalized absolute search key, and absolute, root-equivalent, control-character, and traversal paths are rejected. `PUT /api/index/exclusions` replaces the complete list only while the job lane is idle and immediately schedules reconciliation. The scanner, reconciler, and watcher each resolve the same persisted policy when their session begins. Reconciliation removes newly excluded Lucene entries without touching source files; removing an exclusion lets reconciliation restore eligible entries. See ADR 0024.

The local API exposes `POST /api/index/refresh` for an explicit changed-only repair and `GET /api/index/watch-status` for safe root, state, and message fields. Manual refresh rejects missing roots and overlapping jobs through stable API errors. The frontend polls freshness independently of indexing progress, explains temporary job-time pauses, and offers refresh only for the persisted selected root. See ADR 0018.

## Data flow

The full indexing pipeline is discovery → metadata upsert → bounded extraction queue/workers → same-key content update → commit/progress completion. The incremental pipeline is watcher event → bounded per-root queue → coalesced serial application → metadata/content replacement or subtree delete → burst commit. Reconciliation is metadata snapshot → discovery comparison → changed-only metadata/content replacement → proven-missing pruning → commit. Lucene's near-real-time reader can expose metadata upserts before slower content extraction and the final durable commit. A full document replacement keeps one entry per normalized path because Lucene does not perform partial field updates.

The search pipeline is React's debounced query/filter state → relative `/api/search` request → bean and cross-field validation → safe quote-aware normalization → programmatic Lucene term/phrase query plus non-scoring filter clauses → filename-first ranking → optional zero-result fuzzy filename retry → bounded offset window → explanatory match category → bounded plain-text snippet with highlight offsets → API DTO → safely rendered result card. Balanced double quotes create required phrase clauses across filename, path, and content; ordinary text outside quotes remains required, and an unmatched quote falls back to ordinary text. Optional exact kind/extension and inclusive modified-time/size ranges use indexed terms and Lucene points, so filtering does not rewrite or rescore the text query. The fuzzy retry accepts only one unquoted 4–32-character alphanumeric term, applies the same filters, limits edit distance to one or two and expansions to 50, and never mixes approximate candidates into a successful primary search. Each request over-fetches one scored hit beyond `offset + limit` to derive `hasMore`; totals retain Lucene's exact-versus-lower-bound distinction. See ADRs 0019 through 0022.

During development, Vite proxies relative `/api` traffic to `127.0.0.1:8080`. This keeps browser calls same-origin without widening the backend's network or CORS boundary. The frontend polls indexing status only while a job is running and aborts obsolete search requests when the query changes.

## Concurrency

Discovery, filesystem watching, incremental application, extraction, index writing, and search use separate execution boundaries. The native watch observer hands events to a fixed-capacity application queue; a full queue blocks that observer and native overflow remains detectable. One incremental worker preserves mutation order. Content-indexing workers use a fixed pool and bounded queue; when that queue fills, the discovery caller performs extraction work to apply backpressure instead of accumulating paths. The extractor has its own fixed parser pool and bounded queue so parser capacity remains independently enforced.

## Content extraction

The `ContentExtractor` contract isolates callers from Apache Tika. A conservative extension allowlist is checked first, then Tika detection must confirm a compatible media type before parsing. Initial formats are plain text, Markdown, common source/configuration files, PDF, and DOCX. Embedded documents are disabled.

No-follow basic attributes are checked before submission and again inside the parser worker. Only regular files proceed, and the configured byte limit is enforced at both boundaries. A bounded executor limits concurrency and queued work, Tika's write limit caps extracted characters, and each request has a deadline. Timed-out or caller-interrupted futures are cancelled and purged from the parser queue; caller interruption is preserved. Shutdown cancels queued work and performs only a bounded cooperative wait. Outcomes are explicit: `SUCCESS`, `UNSUPPORTED`, `SKIPPED_TOO_LARGE`, `PERMISSION_DENIED`, `PARSE_ERROR`, or `TIMEOUT`. Failures do not expose parser exception details or file contents.

The current timeout uses interruption of an in-process parser worker. It bounds how long the caller waits but is cooperative rather than hard process isolation. A parser that ignores interruption could occupy a bounded worker until it returns, and a same-user process can still replace a path after the worker's final attribute check. Embedded-document extraction is disabled, and an adversarial regression verifies that XML external entities are not resolved into extracted content. Tika 4 forked processing is a future isolation option that requires runtime and packaging work rather than a dependency-only upgrade. See ADRs 0009 and 0027.

## Storage roles

- Lucene: authoritative full-text index for filename, path, metadata, and extracted content.
- SQLite: indexed roots, settings, scan jobs, progress checkpoints, and categorized scan failures. Exclusions remain a future extension.
- Filesystem: read-only source data. DeepFind does not modify indexed files.

## Security boundary

The backend binds to `127.0.0.1`, never `0.0.0.0`. `LocalApiRequestFilter` rejects non-loopback Host/Origin/Referer metadata and explicit cross-site fetches. Every unsafe HTTP method also requires the fixed `X-DeepFind-Client: browser` header, which prevents ordinary cross-site form submission and forces browser fetches through preflight when cross-origin. The value is intentionally documented and is not authentication against same-device processes. API responses disable storage/sniffing and restrict cross-origin resource use. No CORS allowlist is installed, and the unused Actuator dependency and routes are absent. See ADR 0025.

## Diagnostics and logging

Application-owned console logs use stable `event=... key=value` records containing only job identifiers, bounded counters, fixed operation names, enums, and exception class names. Paths, queries, extracted content, parser-provided fields, exception messages, request bodies, and throwable stacks are not logger arguments. Spring detailed startup INFO is disabled, third-party output defaults to WARN, and direct Flyway/Tika/PDFBox/POI logging is disabled. Database migration failures are translated into a path-free event and safe startup exception. Watcher and incremental-worker threads have fixed names rather than root-derived hashes. SQLite scan history remains intentionally path-bearing local application state, not a log. See ADR 0026.

## Runtime network behavior

Production backend source contains no outbound HTTP/cloud client, telemetry exporter, crash uploader, analytics service, or updater. The frontend calls only relative `/api` paths and packages no remote assets; its production runtime dependencies are React and ReactDOM. The embedded server listens on IPv4 loopback, JMX is explicitly disabled, Actuator is absent, and the unused Tomcat WebSocket runtime is excluded. Micrometer observation types arrive through Spring Web but no meter registry, exporter, or observation endpoint is packaged.

`scripts/audit-runtime-network.ps1` launches the packaged backend with isolated temporary state and inspects endpoints owned by that process during startup, health access, and idle sampling. The Phase 7 trace observed only `127.0.0.1:18082` listening and no UDP endpoint. This sampled evidence complements source/dependency review; it does not replace re-auditing after dependency, parser, or desktop-shell changes. Build tools can contact dependency repositories, but the packaged application has no designed external runtime request. See ADR 0028.

## Runtime configuration and API

Spring owns one Lucene index lifecycle and closes it on shutdown. The index defaults to `${user.home}/.deepfind/index`; `DEEPFIND_DATA_DIRECTORY` overrides the shared parent data directory for packaging and tests. A SQLite database at `<data-directory>/deepfind.db` stores normalized indexed-root records and generic application settings. Flyway migrates it before persistence-backed services initialize. SQLite foreign keys, a five-second busy timeout, write-ahead logging, and normal synchronous mode are configured on each connection. Startup fails on invalid migrations or database corruption instead of deleting state.

Selecting a valid indexing root transactionally upserts its normalized record and the `last_selected_root` setting before the background job starts. The job is then inserted as `RUNNING`. Progress is checkpointed every 250 discovered entries or two seconds, whichever occurs first, avoiding a database write for every file. Discovery failures are stored separately with stable categories and bounded messages. Completion stores final counters and updates `last_indexed_at`; terminal failures are also persisted.

On startup, any abandoned `RUNNING` row becomes `INTERRUPTED` rather than completed or deleted. The latest interruption is exposed as the current status with a restart-to-reconcile message, while committed Lucene results remain searchable. A bounded `GET /api/index/history` endpoint exposes recent history through API DTOs. The existing frontend restores the root and allows a new full scan to reconcile it; true mid-tree continuation is not claimed. See ADRs 0012 and 0013.

One daemon worker accepts at most one indexing job at a time, while search uses Lucene's independently refreshed readers. The local API exposes indexing start/status and filename, path, and content search. Search offsets are bounded at 10,000 and page sizes at 1,000; the current interface uses batches of 50. Requests are validated and failures use stable error codes without Java stack traces. Unknown resources return a stable 404 rather than being misclassified as internal failures.

## Index model

Lucene schema version 3 indexes filenames, paths, and extracted content with a delimiter-aware lowercase analyzer. A normalized absolute-path key is exact and unique for upsert/delete. Display metadata, extraction status/reason, and an extraction-bounded snippet source are stored locally. Size and timestamps additionally use points for range filters and numeric doc values for sorting. Exact filename, filename prefix, general filename, and filename phrase clauses outrank path and content clauses; exact content phrases outrank general content relevance. Only when that primary query has no matches may bounded filename-token edit distance produce `FUZZY_FILENAME` results. Content results receive whitespace-normalized excerpts with at most 240 body characters and ordered UTF-16 highlight ranges. `SearcherManager` provides near-real-time visibility, while explicit commits provide restart durability. Schema version lives in commit metadata and incompatible versions fail explicitly; earlier indexes require a rebuild. See ADRs 0006, 0010, 0011, 0019, and 0021.

## Platform integration

Open and reveal actions live behind the `FileActions` platform abstraction and narrow loopback POST endpoints. Requests must identify an existing absolute path. The implementation passes paths as discrete process arguments without shell interpolation: Explorer on Windows, `open` on macOS, and `xdg-open` on Linux. Clipboard operations stay in the browser. See ADR 0008.
