# Progress and Handoff

## Current phase

Phase 2 — Content Extraction (in progress)

## Last completed step

STEP 8 — Connect bounded document extraction to Lucene content search.

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

## Current behavior

The user can index a local folder and search filenames, paths, and text inside supported text, Markdown, common source/configuration, PDF, and DOCX files. Metadata is upserted before bounded content work and malformed content does not remove its filename/path result. Content terms are persisted locally in Lucene but full extracted text is not stored as a retrievable field, so snippets are not available yet. The index defaults to `${user.home}/.deepfind/index` and can be redirected with `DEEPFIND_DATA_DIRECTORY`. Schema version 1 indexes must be removed and rebuilt. Indexed roots are not yet persisted as configuration.

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

## Known failures

No product failures recorded. Maven is not installed globally, so all backend commands use the checked-in wrapper; its Windows launcher includes a compatibility guard for a normal, non-symbolic-link `.m2` directory. Tests emit non-failing Mockito future-JDK and Lucene optional-vector-optimization warnings. Symlink behavior is covered conditionally and should also run in CI on a host that permits symlink creation. Extraction timeouts currently use cooperative thread interruption, not hard process isolation; a parser that ignores interruption can retain a bounded worker until it exits. Schema version 1 indexes are rejected and require manual local-index removal/rebuild until rebuild controls exist. Scanned PDFs require future OCR.

## Next recommended step

Add bounded content snippets around matching terms, expose them through the search API, and render safe highlighted context in result cards. Complete Phase 2 with an end-to-end content-search acceptance corpus.

## Important architectural notes

- Java 21 and Spring Boot 4.1.1 backend.
- React 19 + TypeScript + Vite frontend.
- Runtime services bind to loopback only.
- Discovery uses observer callbacks so a later bounded indexing queue can apply backpressure.
- Directory symlinks are indexed but not followed; see ADR 0005.
- Lucene schema version 2 content fields, ranking, and bounded pipeline are documented in ADR 0010; ADR 0006 records the original metadata schema.
- The asynchronous loopback API and process-local job policy are documented in ADR 0007.
- Validated, shell-free platform action behavior is documented in ADR 0008.
- Tika extraction policy and cooperative-timeout tradeoffs are documented in ADR 0009.
- SQLite and the desktop shell remain deferred until their owning steps/phases.
