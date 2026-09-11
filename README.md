# DeepFind

> You remember what was in the file. DeepFind finds where you put it.

DeepFind is a private, offline-first desktop search application for finding files by name, path, and the text inside supported documents. **Phases 1–6 are complete.** Users can select a local folder by path, retain that selection and scan history across restarts, monitor or pause indexing, exclude selected subfolders, recover clearly from interrupted scans, search filenames, paths, text, Markdown, common source files, PDF, and DOCX, see highlighted match context, then open, reveal, or copy result paths from the web interface. The selected root is watched automatically, so ordinary create, edit, rename, and delete activity updates Lucene after indexing. Periodic metadata-aware reconciliation repairs missed events and proven deletions, while the interface exposes live-update health and a manual changed-only refresh.

## Privacy baseline

DeepFind is designed to process files locally. Core functionality will not require an account, cloud API, telemetry, or document uploads. The local backend binds to `127.0.0.1`, rejects non-local browser/authority metadata, and exposes no broad CORS policy. Default operational logs use safe event categories and never include full paths, search queries, extracted text, or exception messages.

## Repository layout

```text
backend/      Spring Boot local API and future indexing engine
frontend/     React + TypeScript user interface
docs/         Product, architecture, privacy, progress, and decisions
```

## Prerequisites

- Java 21
- Node.js 22.12 or newer
- npm 11 or newer

Maven does not need to be installed globally; use the checked-in Maven Wrapper.

## Run locally

Backend (PowerShell):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend (a second terminal):

```powershell
cd frontend
npm install
npm run dev
```

The backend listens only on `http://127.0.0.1:8080`. The frontend development server listens on `http://127.0.0.1:5173` and proxies relative `/api` requests to that loopback backend.

All state-changing `/api` requests require `X-DeepFind-Client: browser`. This is a CSRF barrier used automatically by the frontend, not authentication or a secret against other software running on the same computer. Read requests accept no cross-origin access, API responses disable caching and sniffing, and unused Spring Actuator HTTP endpoints are not packaged.

## Verify

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm ci
npm run check
```

`npm run check` runs frontend linting, tests, and the production build.

## Packaging

Desktop packaging is intentionally deferred to Phase 8. The production goal is one self-contained desktop application that bundles its required runtime.

## Local data

Local data defaults to `${user.home}/.deepfind`. Lucene keeps the search index under `index`, while SQLite keeps indexed-root records, application settings, scan history, progress checkpoints, and categorized scan failures in `deepfind.db` (with transient `deepfind.db-wal` and `deepfind.db-shm` files possible while running). Set `DEEPFIND_DATA_DIRECTORY` to override the shared parent directory. Flyway applies explicit database migrations at startup; a migration or corruption error stops startup instead of silently replacing local state.

Extracted text is indexed into local Lucene postings. An extraction-bounded stored copy supports snippets; search responses return only a short excerpt of at most 240 content characters plus boundary ellipses. Extraction defaults are a 20 MiB file limit, 500,000 extracted characters, a 15-second deadline, two workers, and a queue capacity of 32. Override them with the `deepfind.extraction.*` Spring properties when developing or packaging.

Incremental indexing uses a fixed event queue of 256 entries and waits up to 30 seconds for graceful shutdown. Override these development defaults with `deepfind.watcher.queue-capacity` and `deepfind.watcher.shutdown-timeout`. When the queue is full, the watcher producer waits instead of allocating an unbounded backlog.

Reconciliation begins 30 seconds after startup and then runs at most every 15 minutes by default. It scans metadata but re-extracts content only for new, changed, or previously incomplete files, and removes entries only when their source paths are proven absent. Override the cadence with `deepfind.reconciliation.interval`, `deepfind.reconciliation.initial-delay`, and `deepfind.reconciliation.poll-interval`.

**Pause indexing** requests a safe stop after the current discovery boundary and any in-flight extraction work. Completed index changes are committed, the paused job and counters are stored in SQLite, and native watching resumes. **Resume indexing** starts a new changed-only reconciliation job rather than trusting an obsolete directory cursor, so files changed during the pause are repaired safely. `POST /api/index/pause` and `POST /api/index/resume` expose the same lifecycle to local clients.

**Folders to skip** accepts one path per line relative to the persisted selected root. Saving validates and stores the per-root list locally, then starts changed-only reconciliation so newly excluded index entries disappear and newly included paths return. Full scans, reconciliation, and live watching share this policy. The built-in generated-folder exclusions remain active, and no source file or folder is deleted or changed. Local clients can read and replace the list through `GET` and `PUT /api/index/exclusions`.

## Search behavior

Ordinary words are required but may occur across indexed filename, path, or content fields. Wrap words in balanced double quotes to require their analyzed order and adjacency, for example `"annual budget report"`. Quoted phrases can be combined with ordinary words. An unmatched quote is treated as ordinary text instead of exposing query-parser errors. Filename matches continue to outrank path and content matches, and content phrase results are labeled **Exact phrase**.

When an ordinary single word of 4–32 letters or digits has no exact filename, path, or content result, DeepFind performs one filename-only spelling fallback. Four- and five-character terms allow one edit; longer terms allow at most two, with a fixed candidate-expansion cap. These results are labeled **Similar filename**. Short, multi-word, and quoted searches are never fuzzy, and a normal result always suppresses the fallback.

The search interface can narrow results by entry type, extension, recent modification window, and file-size range. Filters are sent as separate validated API parameters rather than embedded in the query text, so they restrict candidates without changing relevance scores. The loopback API accepts optional `kind`, `extension`, `modifiedAfter`, `modifiedBefore`, `minSizeBytes`, and `maxSizeBytes` parameters on `GET /api/search`; date values are ISO-8601 instants and numeric ranges are inclusive bytes.

Search responses are paged. The API accepts an `offset` from 0 through 10,000 and a `limit` from 1 through 1,000, and returns `offset`, `limit`, `hasMore`, and `totalHitsExact` alongside the results. The interface requests 50 results initially and appends the next page through **Load more**. A `+` after the displayed total means Lucene reported a safe lower bound rather than an exact count.

## Documentation

- [Product requirements](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Search quality evaluation](docs/SEARCH_QUALITY.md)
- [Privacy](docs/PRIVACY.md)
- [Progress and handoff](docs/PROGRESS.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture decisions](docs/decisions/README.md)
