# Privacy

## Current behavior

DeepFind indexes selected filesystem metadata and supported document text into a local Lucene directory. A local SQLite database stores normalized selected-root paths, selection/index timestamps, application settings, scan counters, current checkpoint paths, and categorized scan-failure paths/messages. The default data directory is `${user.home}/.deepfind`, and `DEEPFIND_DATA_DIRECTORY` can override it. Extraction runs locally under byte, character, concurrency, and time limits. Only regular files proceed after no-follow attribute and size checks before submission and inside the parser worker. Lucene stores searchable postings and an extraction-bounded text copy used only to create result snippets; the entire data directory must be treated as sensitive local data. Search responses expose only short matching excerpts, and React renders document text without interpreting it as HTML. Search queries and extracted content are not logged by application code. DeepFind's Java/React implementation does not send telemetry, load remote fonts, call cloud APIs, or upload indexed metadata or content. The embedded browser has a separate privacy boundary described below.

## Product policy

- File metadata and supported content will be processed locally.
- The search index, database, settings, and logs will remain on the device.
- Core features will work without login or an internet connection.
- Extracted content and private queries will not be logged by default.
- No analytics, tracking, crash-upload, or external AI service will be silently introduced.
- Any future online feature must be explicit, optional, disabled by default, and clearly disclosed.

## Network behavior

### Windows desktop boundary

The Java/React source audit is not a claim about every behavior of the embedded WebView2 runtime. The first full desktop process-tree audit observed an external HTTPS connection owned by WebView2's network service; no payload was captured, so this does not establish what data was transmitted. The desktop shell now configures an application-only rejecting HTTP/HTTPS proxy, with loopback bypass for its local backend. It never reads, logs, resolves, or forwards rejected requests. This leaves system proxy/firewall/privacy settings unchanged and is not a guarantee against every native WebView2 protocol or service. Use `scripts/audit-desktop-network.ps1` for process-tree sampling; see ADR 0031 and the current verification record before making stronger claims.

WebView2 may maintain its own local browser cache/crash data and follows Microsoft's runtime privacy/servicing behavior. DeepFind does not enable an application updater or upload indexed documents; the Microsoft Evergreen updater is separate and outside the process-tree audit. See [Microsoft's WebView2 data and privacy documentation](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/data-privacy).

### Backend and frontend source boundary

The runtime backend binds to IPv4 loopback only. The Vite development server also binds to loopback and proxies relative `/api` requests to the backend; no broad CORS policy is enabled. API requests with a non-local host, origin, referrer, or cross-site fetch signal are rejected, and state-changing calls require a frontend-set custom header so ordinary hostile web forms cannot trigger them. That header is not a secret and does not authenticate software already running locally. JMX is explicitly disabled, unused Actuator and WebSocket surfaces are not packaged, and production code contains no cloud client, telemetry exporter, crash uploader, analytics service, or updater. Open/reveal requests pass an existing local path to the operating system as a discrete process argument and never interpolate it into a shell command. Clipboard operations remain inside the local browser session.

Frontend requests use relative `/api` paths and the production bundle contains no remote fonts, stylesheets, scripts, or images. Its runtime package graph contains React and ReactDOM only; development dependencies are build-time tools and are not shipped as runtime services. Apache Tika runs in-process with embedded-document extraction disabled, and adversarial regressions verify that XML external entities cannot read a local file or trigger an HTTP request. A packaged-JAR trace covering startup, loopback health, and idle sampling observed only the `127.0.0.1` listener and no UDP endpoint. The repeatable Windows audit lives at `scripts/audit-runtime-network.ps1`. Sampling cannot prove every possible third-party path, so the audit must be rerun and extended when dependencies, formats, packaging, or online features change. Maven, npm, and development tools may contact configured package repositories while installing dependencies; that is build-time behavior, not application telemetry.

## Storage disclosure

The Lucene search index is stored under `<data-directory>/index`. SQLite structured state is stored in `<data-directory>/deepfind.db`; `deepfind.db-wal` and `deepfind.db-shm` may exist while the application is running. Neither store is encrypted by DeepFind. The SQLite database contains indexed roots, per-root exclusions, settings, scan failures, counters, checkpoints, and timestamps but not extracted document contents; those remain in Lucene. The history API is loopback-only and returns at most 100 jobs per request. No extraction temporary files are created by the current implementation. Application logs remain console-only and use stable events containing job identifiers, bounded counters, fixed categories, and exception class names. They do not include full paths, private queries, extracted text, parser-provided metadata/reasons, request bodies, exception messages, or throwable stacks. Detailed Spring startup INFO and direct migration/parser library logs are disabled by default. DeepFind will not claim its local state is encrypted until encryption is implemented and verified.
