# Privacy

## Current behavior

DeepFind indexes selected filesystem metadata and supported document text into a local Lucene directory. A local SQLite database stores normalized selected-root paths, selection/index timestamps, application settings, scan counters, current checkpoint paths, and categorized scan-failure paths/messages. The default data directory is `${user.home}/.deepfind`, and `DEEPFIND_DATA_DIRECTORY` can override it. Extraction runs locally under byte, character, concurrency, and time limits. Lucene stores searchable postings and an extraction-bounded text copy used only to create result snippets; the entire data directory must be treated as sensitive local data. Search responses expose only short matching excerpts, and React renders document text without interpreting it as HTML. Search queries and extracted content are not logged by application code. The application does not send telemetry, load remote fonts, call cloud APIs, or upload indexed metadata or content.

## Product policy

- File metadata and supported content will be processed locally.
- The search index, database, settings, and logs will remain on the device.
- Core features will work without login or an internet connection.
- Extracted content and private queries will not be logged by default.
- No analytics, tracking, crash-upload, or external AI service will be silently introduced.
- Any future online feature must be explicit, optional, disabled by default, and clearly disclosed.

## Network behavior

The runtime backend binds to loopback only. The Vite development server also binds to loopback and proxies relative `/api` requests to the backend; no broad CORS policy is enabled. API requests with a non-local host, origin, referrer, or cross-site fetch signal are rejected, and state-changing calls require a frontend-set custom header so ordinary hostile web forms cannot trigger them. That header is not a secret and does not authenticate software already running locally. Unused Actuator web endpoints are not packaged. Open/reveal requests pass an existing local path to the operating system as a discrete process argument and never interpolate it into a shell command. Clipboard operations remain inside the local browser session. Development tools may contact package repositories while installing dependencies; that is build-time behavior, not application telemetry. Apache Tika runs in-process and performs no application-configured outbound requests; embedded-document extraction is disabled.

## Storage disclosure

The Lucene search index is stored under `<data-directory>/index`. SQLite structured state is stored in `<data-directory>/deepfind.db`; `deepfind.db-wal` and `deepfind.db-shm` may exist while the application is running. Neither store is encrypted by DeepFind. The SQLite database contains indexed roots, per-root exclusions, settings, scan failures, counters, checkpoints, and timestamps but not extracted document contents; those remain in Lucene. The history API is loopback-only and returns at most 100 jobs per request. No extraction temporary files are created by the current implementation. Application logs remain console-only and record job identifiers and exception classes, not document contents, private queries, or full paths. DeepFind will not claim its local state is encrypted until encryption is implemented and verified.
