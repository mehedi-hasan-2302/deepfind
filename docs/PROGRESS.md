# Progress and Handoff

## Current phase

Phase 2 — Content Extraction (in progress)

## Last completed step

STEP 7 — Add a bounded Apache Tika content-extraction foundation.

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

## Current behavior

The frontend and backend provide the complete Phase 1 metadata-search loop. The backend can also detect and extract bounded text from supported local documents through an isolated service boundary. Extraction is not yet connected to the discovery/indexing job or Lucene schema, so searches still match filename and path only. Extracted content is currently transient and stays in memory. The Lucene index defaults to `${user.home}/.deepfind/index` and can be redirected with `DEEPFIND_DATA_DIRECTORY`. Indexed roots are not yet persisted as configuration.

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

## Known failures

No product failures recorded. Maven is not installed globally, so all backend commands use the checked-in wrapper; its Windows launcher includes a compatibility guard for a normal, non-symbolic-link `.m2` directory. Tests emit non-failing Mockito future-JDK and Lucene optional-vector-optimization warnings. Symlink behavior is covered conditionally and should also run in CI on a host that permits symlink creation. Extraction timeouts currently use cooperative thread interruption, not hard process isolation; a parser that ignores interruption can retain a bounded worker until it exits.

## Next recommended step

Add a versioned Lucene content field and connect the discovery/indexing pipeline to bounded extraction. Then add content-aware ranking and snippets so a term found only inside a supported document returns that file.

## Important architectural notes

- Java 21 and Spring Boot 4.1.1 backend.
- React 19 + TypeScript + Vite frontend.
- Runtime services bind to loopback only.
- Discovery uses observer callbacks so a later bounded indexing queue can apply backpressure.
- Directory symlinks are indexed but not followed; see ADR 0005.
- Lucene schema version 1 and field behavior are documented in ADR 0006.
- The asynchronous loopback API and process-local job policy are documented in ADR 0007.
- Validated, shell-free platform action behavior is documented in ADR 0008.
- Tika extraction policy and cooperative-timeout tradeoffs are documented in ADR 0009.
- SQLite and the desktop shell remain deferred until their owning steps/phases.
