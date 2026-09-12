# Progress and Handoff

## Current phase

Phase 8 — Desktop Packaging (installable unsigned preview delivered; clean-machine acceptance pending)

## Last completed step

STEP 32 — Build and locally verify the Windows preview installer.

## Completed

- STEP 32: `artifacts/DeepFind-Setup.exe` built successfully and tested as a per-user installation in a path containing spaces. Bundled-runtime launch, installed PDF/DOCX/text search, watching, duplicate launch, normal shutdown, restart persistence, backend recovery, shell-crash cleanup, open/reveal, sampled network checks, and uninstall/data preservation passed. The test installation was removed; the installer remains. See [the exact artifact and verification record](WINDOWS_PREVIEW_VERIFICATION.md) and [the learning/install guide](INSTALLATION.md). This is an unsigned preview, not a clean-machine production sign-off.
- STEP 31: native shell now supervises the packaged Java backend, handles Windows resource paths correctly, displays local startup/recovery/closing pages, and uses an app-only rejecting WebView HTTP/HTTPS proxy. Four release-profile Rust tests passed. Third-party notices are included. See ADRs 0030 and 0031.
- STEP 30: Rust 1.98.1/MSVC toolchain installed, Tauri 2.11.5 shell compiled, bundled local page verified in a real Windows window through accessibility, and window closed successfully. Screenshot capture is unavailable on this Windows capture interface (`SetIsBorderRequired`, 0x80004002); accessibility verification works. The first executable is a shell spike, not yet the final integrated application.
- STEP 29: desktop Maven resource profile, production UI CSP/security headers, inherited-pipe readiness/shutdown protocol, repeatable preparation script, and private Java 21 image. Isolated bundled-runtime smoke testing passed text/PDF/DOCX extraction, live watching, SQLite/Lucene restart persistence, and graceful shutdown. Rust and Microsoft C++ Build Tools 2022 are installed; native shell compilation is in progress.
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
- Per-selected-root exclusion settings stored in SQLite as validated, normalized relative paths while retaining immutable built-in generated-folder exclusions.
- One shared exclusion policy across full scans, changed-only reconciliation, and live watcher sessions, with list replacement serialized through the single job lane.
- Immediate safe reconciliation after an exclusion change so newly skipped Lucene entries are removed and newly included entries can return without modifying source data.
- Loopback read/replace exclusion endpoints, stable validation errors, and an accessible one-path-per-line indexing-panel editor with explicit privacy guidance.
- Unit, HTTP integration, and React regressions for normalization, deduplication, persistence, traversal rejection, result pruning, and save/reconciliation behavior.
- Phase 6 exit criteria met: indexing progress, counters, current path, pause/resume recovery, concurrent search, live-update health, refresh, and exclusions are understandable from the interface.
- Audited backend and development-server binding, CORS behavior, browser request metadata, response headers, generic route handling, and unused management endpoints.
- Highest-precedence API filtering that rejects non-loopback host authorities, non-local origins/referrers, and explicit cross-site fetches before controller dispatch.
- State-changing API requests require the frontend-set `X-DeepFind-Client` CSRF barrier; it is intentionally documented as neither a secret nor authentication for other local processes.
- Defensive API response policy with no-store caching, MIME sniffing prevention, same-origin resource policy, no-referrer policy, and a deny-by-default content security policy.
- Unused Spring Boot Actuator web endpoints removed from the packaged backend, with unknown resources mapped to a stable safe HTTP 404 response.
- HTTP integration regressions for hostile authority/origin/referrer/fetch metadata, missing mutation protection, permitted loopback traffic, response headers, absent permissive CORS, and absent Actuator surface.
- Complete application logging-call audit with stable `event=... key=value` records for lifecycle, scan, extraction, commit, watcher, migration, and platform-action events.
- Path-, query-, document-, parser-field-, exception-message-, request-body-, and throwable-free logger arguments, backed by targeted privacy regressions using deliberately sensitive test values.
- Default suppression of detailed Spring startup INFO and direct Flyway/Tika/PDFBox/POI logging, while third-party logging defaults to WARN and application events remain visible at INFO.
- Safe Flyway migration wrapper that preserves a categorized failure event without exposing a JDBC path through the propagated startup exception.
- Fixed watcher and incremental-worker thread naming with no selected-root-derived hashes, plus privacy-safe watcher uncertainty/failure categories.
- No-follow regular-file and byte-limit checks both before extraction submission and inside the parser worker, with stable outcomes for non-regular, unavailable, unreadable, and oversized inputs.
- Deadline and interruption cancellation now purges queued futures so abandoned requests do not retain bounded parser capacity behind a non-cooperative worker.
- Caller interrupt preservation, explicit post-shutdown outcomes, cancellation of drained shutdown work, and a bounded cooperative shutdown wait.
- Adversarial regressions for non-regular input, conditional symbolic links, malformed PDFs, interruption-ignoring parsers, caller interruption, closed extractors, and XML external entities.
- Explicit normal-user permission policy with no whole-application elevation, plus a documented residual in-process parser and same-user path-replacement boundary.
- Tika release and security-model review retaining supported 3.3.2 modules while deferring Tika 4 forked processing to a dedicated runtime/packaging decision.
- Production source and resolved dependency audit finding no backend HTTP/cloud client, telemetry exporter, crash uploader, analytics service, updater, remote frontend asset, or non-relative frontend API request.
- Explicit JMX disablement and removal of the unused embedded Tomcat WebSocket runtime, while retaining the already-tested absence of Actuator endpoints and broad CORS.
- HTTP external-entity regression proving hostile XML cannot trigger even a loopback request, complementing the local-file entity regression.
- Repeatable Windows packaged-runtime audit that isolates local state, samples process-owned TCP/UDP endpoints, enforces IPv4 loopback-only listening, and cleans its validated temporary directory.
- Live packaged-JAR evidence covering startup, health, and idle sampling: one `127.0.0.1:18082` listener, no external TCP connection, and no UDP endpoint.
- Phase 7 exit criteria met: no designed external runtime request, no observed unexpected packaged-runtime activity in the audited paths, and no obvious unsafe local API behavior.
- Tauri 2 selected over Electron, JavaFX, and a system-browser launcher after reviewing UI reuse, lifecycle ownership, packaged surface, offline installation, and build/runtime prerequisites.
- Desktop architecture fixed around a minimal Rust supervisor, production React assets served by Spring Boot, an exact ephemeral IPv4 loopback origin, and no generic native capability exposed to frontend JavaScript.
- Application-specific Java 21 `jlink` runtime selected with a broader-runtime fallback until reflection, service loading, native libraries, parsers, persistence, watching, and platform actions pass installed-app tests.
- Per-user NSIS `DeepFind-Setup.exe`, installed `DeepFind.exe`, bundled WebView2 offline installer, preserved `${user.home}/.deepfind` state, and no MVP auto-updater selected as distribution policy.
- Staged packaging implementation and clean-Windows acceptance evidence documented in `docs/PACKAGING.md`, with lifecycle/security tradeoffs captured in ADR 0029.

## Current behavior

The local HTTP boundary rejects non-local browser and authority metadata, requires a CSRF header on state-changing API calls, emits defensive response headers, and packages no Actuator web surface. This does not authenticate other processes running as the current user.

Default runtime logs are console-only structured events containing operational categories, identifiers, and bounded counters. They omit full paths, search queries, document text, parser-provided fields, exception messages, request bodies, and throwable stacks; detailed framework/parser output is suppressed.

Content extraction now rejects static symbolic links and non-regular inputs with no-follow checks at submission and worker entry, purges cancelled queue entries after deadlines or caller interruption, preserves caller interruption, and reports shutdown explicitly. The parser remains in-process and cooperative rather than a hard security sandbox.

The production application has no designed external request. Backend source and dependencies contain no configured client/exporter/updater, frontend runtime calls are relative and use no remote assets, JMX/WebSocket/Actuator surfaces are absent or disabled, XML external entities cannot trigger HTTP, and the packaged runtime audit observes only its loopback listener. Build dependency downloads remain separate build-time network behavior.

Desktop packaging now has a documented architecture but no generated `.exe` yet. Tauri will own the window and supervise a bundled Java backend; the implementation deliberately begins with a shell spike before runtime minimization or installer generation.

Each selected root can now persist a validated list of relative folders to skip. Saving the list triggers changed-only reconciliation, and full scans, reconciliation, and live watching all use the same policy without modifying source files.

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
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 23 — passed; 92 tests covered persisted exclusions and HTTP reconciliation, executable JAR packaging and Spotless succeeded, and two host-dependent symbolic-link tests were skipped.
- `npm run check` in `frontend` after STEP 23 — passed; ESLint, 14 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 24 — passed; 93 tests covered the local API request boundary and absence of unused management routes, executable JAR packaging and Spotless succeeded, and two host-dependent symbolic-link tests were skipped.
- `npm run check` in `frontend` after STEP 24 — passed; ESLint, 14 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 25 — passed; 96 tests covered privacy-safe diagnostic fields, migration failure sanitization, fixed watcher thread names, executable JAR packaging, and Spotless, with two host-dependent symbolic-link tests skipped.
- STEP 25 packaged-runtime smoke test — `GET http://127.0.0.1:18080/api/health` returned HTTP 200; startup output omitted the user/working directory, database path, and detailed framework/migration INFO.
- `npm run check` in `frontend` after STEP 25 — passed; ESLint, 14 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- Focused STEP 26 extraction verification — passed; 17 tests covered input boundaries, queue purging, interruption, shutdown, parser failures, and XML external entities, with one host-dependent symbolic-link test skipped.
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 26 — passed; 102 tests covered the hardened parser boundary and all existing behavior, executable JAR packaging and Spotless succeeded, and three host-dependent symbolic-link tests were skipped.
- `npm run check` in `frontend` after STEP 26 — passed; ESLint, 14 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- Focused STEP 27 parser verification — passed; 7 tests included local-file and HTTP external-entity regressions with zero unexpected HTTP requests.
- `backend\mvnw.cmd spotless:apply verify --batch-mode --no-transfer-progress` after STEP 27 — passed; 103 tests, executable JAR packaging, and Spotless succeeded, with three host-dependent symbolic-link tests skipped.
- `npm run check` in `frontend` after STEP 27 — passed; ESLint, 14 Vitest interaction tests, TypeScript, and the Vite production build succeeded.
- STEP 27 resolved dependency/package audit — passed; no analytics, telemetry exporter, crash uploader, cloud client, updater, Actuator, or embedded Tomcat WebSocket runtime was found. Frontend production dependencies were React and ReactDOM only.
- `scripts\audit-runtime-network.ps1` after STEP 27 — passed against the rebuilt packaged JAR; health returned HTTP 200, the only observed endpoint was the `127.0.0.1:18082` TCP listener, and no UDP endpoint was observed.
- STEP 28 local packaging-prerequisite audit — Java 21, `jlink`, `jpackage`, Node.js 22, and npm 11 are available; Rust/Cargo and WiX are not installed. No new runtime dependency was added during the decision step.

## Known failures

STEP 29 verification: 107 backend tests discovered (104 passing, 3 skipped), 14 frontend tests passing; `scripts/prepare-desktop.ps1` and `scripts/test-desktop-runtime.ps1` passed. Toolchain installation and formatting failures were resolved. The integrated shell exposed two startup defects: resolving a page relative to temporary `about:blank`, and passing a Windows verbatim JAR path to Java. Both were corrected; a release launch reached the working React interface, and normal close returned exit code 0 with Java cleanup. Windows debug linking later failed with PDB `FILE_SYSTEM (3)`; the reproducible packaging script now tests the release profile. Generated debug outputs were removed to recover disk space.

Maven uses the checked-in wrapper. Rust/MSVC and NSIS are installed. Restricted Windows builds may require permission to replace generated output. Do not rebuild or bundle while a test application is using its adjacent Java runtime: Windows locks those DLLs. The first full desktop network audit observed a WebView2 external HTTPS connection; after adding an app-scoped rejecting HTTP/HTTPS proxy, two installed-process-tree audits passed. See ADR 0031 for limits rather than relying on the earlier backend-only audit. Final NSIS packaging initially timed out; retrying the bundler succeeded. The installed uninstaller finishes through an asynchronous worker, so checks must wait for both executable and registration removal.

Remaining product limitations: conditional symlink tests, native watcher uncertainty repaired by reconciliation, cooperative parser interruption rather than hard isolation, same-user path-replacement races, no OCR, and an unencrypted local index/database. Older Lucene schemas need deliberate rebuild; corrupt SQLite state intentionally prevents startup and must be preserved before repair. Interrupted runs use reconciliation rather than unsafe cursor continuation. The preview still uses a typed folder path. Clean-machine offline verification and publisher signing remain separate release gates.

## Next recommended step

Install the unsigned preview from `artifacts/DeepFind-Setup.exe` and perform manual UI acceptance. For production release, run the separate clean offline Windows VM checks and arrange publisher signing. No additional feature module is required merely to try this preview. See [WINDOWS_PREVIEW_VERIFICATION.md](WINDOWS_PREVIEW_VERIFICATION.md) for exact evidence and limitations.

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
- Per-root relative exclusion persistence, validation, shared scan/watch policy, and reconciliation behavior are defined by ADR 0024.
- Loopback authority checks, browser-origin defenses, the mutation header, defensive response headers, and the deliberately unauthenticated local-process trust boundary are defined by ADR 0025.
- Privacy-safe structured event fields, dependency-log suppression, migration failure sanitization, and the no-path diagnostics boundary are defined by ADR 0026.
- Double no-follow parser preflight, cancelled-queue purging, cooperative shutdown, normal-user permissions, and residual process-isolation risks are defined by ADR 0027.
- Source/dependency/runtime network auditing, explicit unused-surface removal, future online-feature controls, and packaging re-verification are defined by ADR 0028.
- Tauri shell selection, bundled Java runtime, same-origin loopback UI, supervisor lifecycle, per-user offline installer, and packaging acceptance criteria are defined by ADR 0029.
- Progress checkpoints are intentionally bounded; abrupt termination may lose up to one checkpoint interval of counters, never committed Lucene data.
- Phase 8 implements a Tauri 2 desktop shell/installer with a private runtime, backend supervision, and the documented data directory. The unsigned preview is available; clean-machine production acceptance remains separate.
- Desktop implementation follows the staged spike, backend artifact, supervision, lifecycle, installer, and clean-machine verification plan in `docs/PACKAGING.md`.
