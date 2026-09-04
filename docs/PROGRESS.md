# Progress and Handoff

## Current phase

Phase 1 — Basic File Metadata Search (in progress)

## Last completed step

STEP 2 — Add defensive filesystem discovery.

## Completed

- Java 21 / Spring Boot 4.1.1 backend with Maven Wrapper, formatting gate, loopback-only configuration, and `/api/health`.
- React 19 / TypeScript / Vite frontend with a responsive foundation screen and local-first privacy copy.
- Backend and frontend tests, ESLint, production build, EditorConfig, `.gitignore`, and GitHub Actions CI.
- Product, architecture, privacy, troubleshooting, handoff, and four initial architecture decision records.
- Streaming filesystem discovery with immutable metadata, progress, summaries, and categorized failures.
- Platform-aware path normalization, configurable exclusions, and documented no-follow symlink behavior.
- Temporary-filesystem tests for Unicode metadata, default/explicit exclusions, missing and invalid roots, progress, and symlink handling where supported.

## Current behavior

The backend can defensively scan a selected directory and stream metadata/progress to an observer without retaining the full tree. It skips configured subtrees, records failures without throwing for invalid roots, and indexes symlinks as entries without following directory targets. Lucene indexing, root APIs, search, and result UI do not exist yet.

## Commands verified

- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` — passed; 2 tests, package, and Spotless check succeeded.
- `npm run check` in `frontend` — passed; ESLint, 1 Vitest component test, TypeScript, and Vite production build succeeded.
- Backend runtime smoke test — `GET http://127.0.0.1:8080/api/health` returned HTTP 200 with status `UP`.
- Frontend runtime smoke test — `GET http://127.0.0.1:5173/` returned HTTP 200 with the DeepFind title.
- `backend\mvnw.cmd verify --batch-mode --no-transfer-progress` after STEP 2 — passed; 10 tests, package, and Spotless check succeeded. One symlink test was skipped because this Windows session does not permit symlink creation.

## Known failures

No product failures recorded. Maven is not installed globally, so all backend commands use the checked-in wrapper. Tests emit a non-failing Mockito warning about future JDK dynamic-agent behavior. Symlink behavior is covered conditionally and should also run in CI on a host that permits symlink creation.

## Next recommended step

Add persistent Lucene filename/path metadata indexing: define and document the index schema, consume streamed discovery entries, update by normalized path, reopen the index after restart, and prove filename/path queries against a temporary index.

## Important architectural notes

- Java 21 and Spring Boot 4.1.1 backend.
- React 19 + TypeScript + Vite frontend.
- Runtime services bind to loopback only.
- Discovery uses observer callbacks so a later bounded indexing queue can apply backpressure.
- Directory symlinks are indexed but not followed; see ADR 0005.
- Lucene, SQLite, Tika, and the desktop shell remain deferred until their owning steps/phases.
