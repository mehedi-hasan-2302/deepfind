# Progress and Handoff

## Current phase

Phase 4 — Incremental Updates and Freshness (in progress)

## Last completed step

STEP 14 — Coordinate selected-root watcher lifecycle.

## Completed

- Java 21 / Spring Boot 4.1.1 backend with Maven Wrapper, formatting gate, loopback-only configuration, and `/api/health`.
- React 19 / TypeScript / Vite frontend with a responsive foundation screen and local-first privacy copy.
- Backend and frontend tests, ESLint, production build, EditorConfig, `.gitignore`, and GitHub Actions CI.
- Product, architecture, privacy, troubleshooting, handoff, and four initial architecture decision records.
- Streaming filesystem discovery with immutable metadata, progress, summaries, and categorized failures.
- Platform-aware path normalization, configurable exclusions, and documented no-follow symlink behavior.
- Temporary-filesystem tests for Unicode metadata, default/explicit exclusions, missing and invalid roots, progress, and symlink handling where supported.
- Apache Lucene 10.5.1 metadata index with schema-version validation, normalized-path upsert/delete, explicit commits, near-real-time reader refresh, and clean lifecycle handling.
- Delimiter-aware filename/path analysis, bounded plain-text search, filename-first ranking, and categorical match explanations.
- Discovery-to-index bridge plus integration tests for duplicates, updates, deletion, exclusions, schema mismatch, punctuation input, and reopen persistence.
- Application-managed Lucene storage with a configurable local data directory and clean Spring shutdown.
- Single-worker asynchronous indexing jobs with current progress, completion/failure state, and concurrent-start rejection.
- Validated indexing/status/search endpoints with stable safe error DTOs and full-context HTTP integration coverage.
- Typed frontend API client using relative loopback requests, explicit error mapping, and cancellation support.
- Folder-path onboarding with asynchronous indexing start, running-only status polling, progress metrics, and readable failure states.
- Debounced filename/path search with stale-request cancellation, timing and hit counts, explanatory match badges, responsive result cards, and accessible empty/loading/error states.
- Vite `/api` development proxy to the loopback backend without broadening the backend CORS or network boundary.
- Guarded platform abstraction for opening and revealing existing absolute paths on Windows, macOS, and Linux without shell interpolation.
- Narrow file-action endpoints with stable invalid/unavailable errors and no generic process-execution surface.
- Result-card controls for Open, Show in Folder, Copy Path, and Copy Folder with accessible per-result feedback.
- Apache Tika 3.3.2 behind `ContentExtractor` and `DocumentParser` contracts, with only the text, code, XML, Microsoft, and PDF parser modules selected.
- Conservative extension and detected-media-type checks for text, Markdown, common source/configuration files, PDF, and DOCX; HTML and other stretch formats remain deferred.
- Configurable byte, extracted-character, deadline, worker-count, and queue-capacity limits, with embedded-document extraction disabled.
- Structured extraction outcomes for success, unsupported types, oversized files, permission failures, malformed documents, and timeouts without exposing parser details.
- Real TXT, source-code, PDF, DOCX, corrupt-PDF, size-limit, media-mismatch, permission, parser-failure, and timeout test coverage.
- Lucene schema version 2 with indexed non-stored content plus stored extraction status and stable failure reason fields.
- Metadata-first, same-path content updates through a fixed content-indexing pool and bounded queue with caller-runs backpressure.
- Filename-first search weighting across filename, path, and content fields, with a distinct `CONTENT` match category exposed through the API and UI.
- End-to-end content-only search through the HTTP API and frontend “Document content” result explanation.
- Integration coverage for TXT, PDF, DOCX, malformed PDF metadata survival, changed-content replacement, filename-over-content ranking, and restart persistence.
- Lucene schema version 3 with an extraction-bounded stored snippet source for every successfully extracted document.
- Whitespace-normalized content excerpts with a 240-character body limit, boundary ellipses, Unicode-safe slicing, and validated highlight offsets.
- Plain-text snippet DTOs through the search API and React-node highlighting without document-derived HTML injection.
- Snippet tests for context selection, multiple terms, whitespace, Unicode, response bounds, API mapping, and hostile markup-like document text.
- SQLite JDBC persistence under the shared local data directory, with foreign keys, busy timeout, write-ahead logging, and normal synchronous durability.
- Flyway schema migrations for normalized indexed-root records and generic application settings, applied before dependent services initialize.
- Transactional root-catalog contracts that preserve first-seen time, update selection/index timestamps, and prevent duplicate normalized paths.
- Last-selected-root restoration into idle indexing status so the frontend folder input survives backend restarts without a separate endpoint.
- Migration-upgrade, repeat-migration, Unicode-path, restart, uniqueness, timestamp, corruption-preservation, job-lifecycle, and frontend-restoration tests.
- Flyway schema versions 3 and 4 for durable scan jobs and categorized per-path scan failures.
- Persisted job start, bounded progress checkpoints, exact terminal counters, failures, completion, and safe terminal error messages.
- Startup recovery that reclassifies abandoned running jobs as interrupted while preserving committed Lucene results.
- Honest interrupted-state UI with restored root and a full restart-to-reconcile action; exact filesystem-cursor resume is not claimed.
- Bounded, validated `GET /api/index/history` access through API DTOs, limited to 100 recent records per request.
- Restart, interruption, failure, checkpoint, Unicode history, migration, history API, limit validation, and recovery UI coverage.
- Recursive Java NIO filesystem watch sessions with normalized create, modify, delete, and overflow events.
- Existing and newly created directory registration using the discovery exclusion policy without following directory symbolic links.
- One synchronous daemon event loop per root, no unbounded application queue, categorized safe failures, root-loss detection, and idempotent bounded shutdown.
- Native watcher integration coverage for nested and Unicode paths, event lifecycles, post-start directory creation, exclusions, validation, and conditional symbolic-link behavior.
- Per-root incremental-indexing sessions with a configurable fixed-capacity queue, blocking backpressure, serial event application, burst coalescing, and bounded graceful shutdown.
- Metadata/content replacement for created and modified files, recursive indexing for populated directories moved into a root, and separator-safe subtree deletion.
- Portable rename handling as delete-old plus create-new without unsafe identity inference or prefix collisions.
- Explicit reconciliation-required state for overflow, watcher failure, out-of-root input, unreadable metadata, processing failure, and forced shutdown.
- Incremental integration coverage for content replacement, deletion, directory rename, similarly prefixed siblings, uncertainty signals, exclusions, and shutdown draining.
- Automatic restoration of the persisted selected root into one active native-watcher and incremental-indexing session pair.
- Full indexing integration that stops the native producer, drains accepted incremental work, and resumes watching after either success or failure.
- Root-switch and application-shutdown ordering that closes old producers before consumers and consumers before Lucene dependencies.
- Safe internal stopped, watching, reconciliation-required, and failed states without making an unavailable persisted folder fatal to startup.
- Native end-to-end coverage proving post-start create, content edit, and delete operations update search results, plus root-switch and unavailable-root behavior.

## Current behavior

The user can index a local folder and search filenames, paths, and text inside supported text, Markdown, common source/configuration, PDF, and DOCX files. Metadata is upserted before bounded content work and malformed content does not remove its filename/path result. Content-only results include a short highlighted excerpt rendered safely as text. Lucene persists searchable data and an extraction-bounded source copy for snippets. SQLite persists roots, settings, scan histories, progress checkpoints, and categorized failures. After an interrupted run, existing committed results remain searchable and the restored folder can be fully rescanned. The persisted selected root is watched automatically, and ordinary create, content edit, rename, and delete events update search results. Local data defaults to `${user.home}/.deepfind`, can be redirected with `DEEPFIND_DATA_DIRECTORY`, and is not encrypted. Earlier Lucene schema indexes must be removed and rebuilt. Native overflow and the full-scan watch gap are detected or documented uncertainty but do not yet trigger automatic reconciliation; watcher status is not yet exposed in the UI.

## Commands verified

- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` — passed; 2 tests, package, and Spotless check succeeded.
- `npm run check` in `frontend` — passed; ESLint, 1 Vitest component test, TypeScript, and Vite production build succeeded.
- Backend runtime smoke test — `GET http://127.0.0.1:8080/api/health` returned HTTP 200 with status `UP`.
- Frontend runtime smoke test — `GET http://127.0.0.1:5173/` returned HTTP 200 with the DeepFind title.
- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` after STEP 2 — passed; 10 tests, package, and Spotless check succeeded. One symlink test was skipped because this Windows session does not permit symlink creation.
- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` after STEP 3 — passed; 18 tests, package, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` after STEP 4 — passed; 21 tests, full HTTP flow, concurrency invariant, package, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `npm run check` in `frontend` after STEP 5 — passed; ESLint, 4 Vitest interaction tests, TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` after STEP 5 — passed through the repaired Windows launcher; 21 tests passed and one host-dependent symlink test was skipped.
- STEP 5 live integration smoke test — frontend HTTP 200; proxied `/api/health` returned `UP`; proxied `/api/index/status` returned `IDLE`.
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 6 — passed; 27 tests covered platform commands and HTTP contracts, with one host-dependent symlink test skipped.
- `npm run check` in `frontend` after STEP 6 — passed; ESLint, 5 Vitest interaction tests, TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd clean verify` after STEP 7 — passed; 38 tests, package, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `backend\mvnw.cmd dependency:tree --batch-mode --no-transfer-progress '-Dincludes=org.apache.tika:*'` after STEP 7 — passed; only the selected Tika parser modules and their required Tika support modules are present.
- `backend\mvnw.cmd clean verify --batch-mode --no-transfer-progress` after STEP 8 — passed; 41 tests, package, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `npm run check` in `frontend` after STEP 8 — passed; ESLint, 5 Vitest interaction tests, TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 9 — passed; 44 tests, package, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `npm run check` in `frontend` after STEP 9 — passed; ESLint, 5 Vitest interaction tests (including safe snippet rendering), TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 10 — passed; 48 tests, package, Flyway startup migration, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `npm run check` in `frontend` after STEP 10 — passed; ESLint, 6 Vitest interaction tests (including persisted-root restoration), TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 11 — passed; 51 tests, package, four-version Flyway migration, restart recovery, history API, and Spotless check succeeded. One host-dependent symlink test was skipped.
- `npm run check` in `frontend` after STEP 11 — passed; ESLint, 7 Vitest interaction tests (including interrupted-run recovery guidance), TypeScript, and Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 12 — passed; 59 tests, executable backend JAR packaging, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 13 — passed; 64 tests, executable backend JAR packaging, Spring configuration binding, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 14 — passed; 67 tests, native end-to-end freshness, job lifecycle integration, executable backend JAR packaging, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.

## Known failures

No product failures recorded. Maven is not installed globally, so all backend commands use the checked-in wrapper; its Windows launcher includes a compatibility guard for a normal, non-symbolic-link `.m2` directory. In restricted Windows environments Maven clean/Spotless may need permission to replace the generated `backend\target` tree. Tests emit non-failing Mockito future-JDK and Lucene optional-vector-optimization warnings. Symlink behavior is covered conditionally and should also run in CI on a host that permits symlink creation. Native watch providers may duplicate, coalesce, reorder, or overflow events; reconciliation must repair uncertainty. Extraction timeouts currently use cooperative thread interruption, not hard process isolation; a parser that ignores interruption can retain a bounded worker until it exits. Lucene schema versions 1 and 2 are rejected and require manual local-index removal/rebuild until rebuild controls exist. Scanned PDFs require future OCR. The unencrypted local index stores extraction-bounded source text for snippets, and the unencrypted SQLite database stores local paths, failure descriptions, counters, and timestamps. A corrupt or invalidly migrated SQLite database intentionally prevents startup until preserved and repaired or deliberately replaced. Interrupted runs restart as full root reconciliation scans rather than unsafe mid-tree continuation.

## Next recommended step

Add automatic reconciliation for watcher uncertainty and the full-scan observation gap, without overlapping full indexing jobs; then expose a manual refresh action.

## Important architectural notes

- Java 21 and Spring Boot 4.1.1 backend.
- React 19 + TypeScript + Vite frontend.
- Runtime services bind to loopback only.
- Discovery uses observer callbacks so a later bounded indexing queue can apply backpressure.
- Directory symlinks are indexed but not followed; see ADR 0005.
- Lucene schema version 3 adds the bounded snippet source and offset-based UI contract in ADR 0011; ADRs 0006 and 0010 record the earlier metadata/content decisions.
- The asynchronous loopback API and process-local job policy are documented in ADR 0007.
- Validated, shell-free platform action behavior is documented in ADR 0008.
- Tika extraction policy and cooperative-timeout tradeoffs are documented in ADR 0009.
- SQLite owns structured local state under the shared data directory; see ADR 0012. Scan history, checkpointing, and interrupted-run recovery are defined by ADR 0013.
- Recursive watcher semantics, exclusion behavior, synchronous delivery, and overflow-as-reconciliation-signal are defined by ADR 0014.
- Bounded ordered event application, recursive directory creation, subtree deletion, rename semantics, and reconciliation triggers are defined by ADR 0015.
- Persisted-root restoration, single active-session ownership, full-scan pause/resume, and shutdown ordering are defined by ADR 0016.
- Progress checkpoints are intentionally bounded; abrupt termination may lose up to one checkpoint interval of counters, never committed Lucene data.
- Phase 8 will choose and implement a self-contained Windows desktop shell/installer. The production `.exe` must bundle its runtime, supervise backend health/lifecycle, use the documented data directory, and require no developer tools.
- The desktop shell remains deferred until its owning phase.
