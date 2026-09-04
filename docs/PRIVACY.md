# Privacy

## Current behavior

DeepFind indexes selected filesystem metadata and supported document terms into a local Lucene directory. The default data directory is `${user.home}/.deepfind`, and `DEEPFIND_DATA_DIRECTORY` can override it. Extraction runs locally under byte, character, concurrency, and time limits. Full extracted text is not retained as a retrievable stored field, but Lucene postings contain searchable terms and must be treated as sensitive local data. Search queries and extracted content are not logged by application code. The application does not send telemetry, load remote fonts, call cloud APIs, or upload indexed metadata or content.

## Product policy

- File metadata and supported content will be processed locally.
- The search index, database, settings, and logs will remain on the device.
- Core features will work without login or an internet connection.
- Extracted content and private queries will not be logged by default.
- No analytics, tracking, crash-upload, or external AI service will be silently introduced.
- Any future online feature must be explicit, optional, disabled by default, and clearly disclosed.

## Network behavior

The runtime backend binds to loopback only. The Vite development server also binds to loopback and proxies relative `/api` requests to the backend; no broad CORS policy is enabled. Open/reveal requests pass an existing local path to the operating system as a discrete process argument and never interpolate it into a shell command. Clipboard operations remain inside the local browser session. Development tools may contact package repositories while installing dependencies; that is build-time behavior, not application telemetry. Apache Tika runs in-process and performs no application-configured outbound requests; embedded-document extraction is disabled.

## Future storage disclosure

The Lucene search index is stored under `<data-directory>/index` and is not encrypted by DeepFind. No extraction temporary files are created by the current implementation. Database, settings, and application-log paths will be documented before those stores are introduced. DeepFind will not claim its index is encrypted until encryption is actually implemented and verified.
