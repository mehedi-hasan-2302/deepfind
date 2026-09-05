# Progress and Handoff

## Current phase

Phase 6 — Indexing UX (in progress)

## Last completed step

STEP 22 — Add durable safe-stop indexing pause/resume.

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
- Read-only Lucene metadata snapshots for exact path comparison without loading the indexed tree into application memory.
- Metadata-aware reconciliation that preserves unchanged content, re-extracts new, changed, or incomplete files, and prunes only paths proven missing or newly excluded.
- Configurable scheduled repair after startup, every 15 minutes by default, with prompt retry for explicit watcher uncertainty.
- Shared single-job scheduling, durable history, and watcher pause/resume coordination so reconciliation never overlaps a full indexing job.
- Reconciliation coverage for unchanged-content preservation, new/changed/deleted paths, incomplete extraction recovery, path-prefix boundaries, uncertainty scheduling, and job-lane exclusion.
- Terminal failure publication now follows the durable history write attempt, preventing a transient terminal-status/history inconsistency.
- Asynchronous `POST /api/index/refresh` for changed-only reconciliation of the persisted selected root, with stable missing-root and busy-job errors.
- Read-only `GET /api/index/watch-status` with safe stopped, watching, reconciliation-required, and failed states.
- Frontend live-update health polling, clear pause/recovery wording, and a guarded manual **Refresh index** action that cannot target an unsaved path.
- HTTP, service, error-mapping, and React interaction coverage for refresh, freshness state, job exclusion, and unavailable selection.
- Phase 4 exit criteria met: create, edit, rename, and delete changes update results automatically, with periodic and explicit reconciliation available when event history is uncertain.
- Balanced double-quoted phrase parsing with ordinary required terms outside quotes and graceful unmatched-quote fallback.
- Programmatic positional phrase queries across filename, path, and content without exposing Lucene query operators.
- Filename-first phrase boosting, a distinct `EXACT_PHRASE` explanation, and safe content snippets/highlights for phrase results.
- Ranking regressions for adjacent versus separated terms, mixed phrase-and-term input, filename-over-content ordering, empty quotes, malformed quotes, HTTP mapping, and frontend explanation.
- Typed optional search filters for exact entry kind/extension plus inclusive modified-time and byte-size ranges.
- Controller and cross-field validation with stable invalid-request responses for malformed enum, extension, instant, negative, and reversed-range input.
- Lucene term and point filters applied as non-scoring clauses, preserving phrase semantics and filename-first ranking among eligible results.
- Responsive frontend controls for entry type, extension, recent modification, and binary size presets with debounced cancellation and one-click reset.
- Filter regressions across Lucene, HTTP query mapping, range validation, extension normalization, responsive React controls, URL serialization, and reset behavior.
- Zero-result fuzzy retry for one unquoted 4–32-character alphanumeric filename term, with one or two edits according to length, a required first-character prefix, transpositions, and at most 50 Lucene term expansions.
- Strict primary-result suppression that keeps exact filename, path, phrase, and content candidate sets unchanged whenever the ordinary query succeeds.
- Existing metadata filters preserved during fuzzy retry, with filename-only results labeled **Similar filename** and no misleading content snippet.
- Regressions for representative receipt misspellings, filtered fallback, successful-primary suppression, ineligible short/multi-word/quoted inputs, HTTP enum mapping, and frontend explanation.
- Validated zero-based search offsets through 10,000 and page sizes through 1,000, with the applied window included in every API response.
- One-hit over-fetching for reliable `hasMore` continuation state without depending on an exact Lucene total-hit count.
- Explicit `totalHitsExact` reporting and a readable `+` suffix when Lucene returns a safe lower bound.
- Cancellable frontend **Load more** requests in batches of 50 that preserve the active query and filters, append results, disable duplicate requests, and disappear at the final page.
- Lucene, HTTP validation/serialization, URL, append-preservation, and final-page pagination regressions.
- Deterministic eight-document local evaluation corpus covering the master-plan queries `refund`, `AWS cancellation`, `salary expectation`, `invoice`, and `resume`.
- Explicit top-result expectations plus a full invoice relevance-tier regression: filename prefix → filename token → folder path → document content.
- Evidence-first ranking policy documented so future boost changes begin with a representative failing case rather than raw-score guesswork.
- Existing production ranking weights retained because every common-query and tier-order expectation passed unchanged.
- Phase 5 exit criteria met: common lexical searches now have executable intuitive-order coverage alongside phrase, filter, fuzzy, highlighting, debounce, and pagination regressions.
- Flyway schema version 5 with durable `PAUSING` and `PAUSED` scan states while preserving existing jobs and categorized failures.
- Safe-boundary pause signaling that stops discovery, drains bounded in-flight extraction, commits completed Lucene work, and resumes native filesystem watching.
- Honest resume semantics that validate the selected root and schedule a new changed-only reconciliation job instead of claiming an obsolete path is a durable cursor.
- Single-job exclusion across running, pausing, and paused ownership, with startup recovery distinguishing deliberately paused work from crashes during running/pausing.
- Loopback pause/resume endpoints, stable invalid-transition conflicts, responsive frontend controls, continued pausing-state polling, and clear recovery wording.
- Lifecycle, migration, persistence, API-error, HTTP-route, and React pause/resume regressions.

## Current behavior

The user can index a local folder, safely pause after bounded in-flight work, and resume through changed-only reconciliation. Completed partial work is committed, deliberate pause state survives restart, and abandoned running/pausing work remains classified as interrupted. The user can search filenames, paths, and text inside supported text, Markdown, common source/configuration, PDF, and DOCX files. Ordinary multi-term searches require all terms; balanced double quotes request positional phrase matching and malformed quotes fall back safely. A single eligible plain term with no ordinary results gets a bounded filename-only spelling retry labeled **Similar filename**; successful primary, short, multi-word, and quoted searches are never fuzzed. Filename phrase results retain priority and content phrase results are explained explicitly. Search results can be narrowed by entry type, extension, recent modification, and file size without changing relevance order, and broad result sets can be appended in bounded 50-result pages. Metadata is upserted before bounded content work and malformed content does not remove its filename/path result. Content results include a short highlighted excerpt rendered safely as text. Lucene persists searchable data and an extraction-bounded source copy for snippets. SQLite persists roots, settings, scan histories, progress checkpoints, and categorized failures. The persisted selected root is watched automatically, and ordinary create, content edit, rename, and delete events update search results. Metadata-aware reconciliation runs after startup and periodically, or promptly after explicit watcher uncertainty, without re-extracting unchanged content. The interface displays live-update health and can request immediate refresh of the persisted root. Local data defaults to `${user.home}/.deepfind`, can be redirected with `DEEPFIND_DATA_DIRECTORY`, and is not encrypted. Earlier Lucene schema indexes must be removed and rebuilt. Reconciliation provides eventual rather than atomic filesystem consistency.

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
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 15 — passed; 72 tests, scheduled metadata reconciliation, single-job exclusion, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 16 — passed; 74 tests, manual refresh and watcher-health API coverage, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 16 — passed; ESLint, 8 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 17 — passed; 76 tests, phrase-query and ranking regressions, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 17 — passed; ESLint, 9 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 18 — passed; 79 tests, filter/range validation and Lucene regressions, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 18 — passed; ESLint, 10 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 19 — passed; 82 tests, bounded fuzzy-query and API regressions, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 19 — passed; ESLint, 11 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 20 — passed; 83 tests, pagination window/HTTP validation regressions, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 20 — passed; ESLint, 12 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 21 — passed; 85 tests, deterministic common-query and relevance-tier evaluation, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `backend\mvnw.cmd spotless:apply clean verify --batch-mode --no-transfer-progress` after STEP 22 — passed; 88 tests, pause/resume lifecycle and schema-v5 persistence coverage, executable backend JAR packaging, Spring startup, and Spotless check succeeded, with two host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 22 — passed; ESLint, 13 Vitest interaction tests, TypeScript, and the Vite production build succeeded.

## Known failures

No product failures recorded. Maven is not installed globally, so all backend commands use the checked-in wrapper; its Windows launcher includes a compatibility guard for a normal, non-symbolic-link `.m2` directory. In restricted Windows environments Maven clean/Spotless may need permission to replace the generated `backend\target` tree. Tests emit non-failing Mockito future-JDK and Lucene optional-vector-optimization warnings. Symlink behavior is covered conditionally and should also run in CI on a host that permits symlink creation. Native watch providers may duplicate, coalesce, reorder, or overflow events; scheduled reconciliation repairs uncertainty eventually, not as an atomic snapshot. Extraction timeouts currently use cooperative thread interruption, not hard process isolation; a parser that ignores interruption can retain a bounded worker until it exits. Lucene schema versions 1 and 2 are rejected and require manual local-index removal/rebuild until rebuild controls exist. Scanned PDFs require future OCR. The unencrypted local index stores extraction-bounded source text for snippets, and the unencrypted SQLite database stores local paths, failure descriptions, counters, and timestamps. A corrupt or invalidly migrated SQLite database intentionally prevents startup until preserved and repaired or deliberately replaced. Interrupted runs restart as full root reconciliation scans rather than unsafe mid-tree continuation.

## Next recommended step

Add persisted exclusion controls so non-technical users can omit selected subfolders without editing backend configuration, then reconcile the index safely when exclusions change.

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
- Metadata snapshots, changed-only extraction, proven-missing pruning, scheduled repair, and shared job exclusion are defined by ADR 0017.
- Manual refresh, independent freshness status, stable API errors, and guarded frontend controls are defined by ADR 0018.
- Balanced-quote parsing, programmatic phrase clauses, ranking weights, fallback behavior, and exact-phrase explanation are defined by ADR 0019.
- Explicit filter parameters, validation, non-scoring Lucene composition, and frontend presets are defined by ADR 0020.
- Zero-result-only fuzzy filename eligibility, edit-distance/expansion bounds, filter preservation, and result explanation are defined by ADR 0021.
- Bounded offset windows, one-hit continuation detection, exact/lower-bound totals, frontend batching, and live-index consistency tradeoffs are defined by ADR 0022.
- Durable pausing/paused states, safe-stop commit behavior, watcher restoration, and reconciliation-based resume are defined by ADR 0023.
- Progress checkpoints are intentionally bounded; abrupt termination may lose up to one checkpoint interval of counters, never committed Lucene data.
- Phase 8 will choose and implement a self-contained Windows desktop shell/installer. The production `.exe` must bundle its runtime, supervise backend health/lifecycle, use the documented data directory, and require no developer tools.
- The desktop shell remains deferred until its owning phase.
