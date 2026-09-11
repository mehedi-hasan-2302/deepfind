# 0026 — Use privacy-safe structured operational logs

## Context

DeepFind handles filenames, paths, search queries, and extracted document text that can be highly sensitive. The existing application-owned failure messages logged only job identifiers and exception classes, but default framework startup INFO could disclose the user name and working directory, Flyway could print the SQLite location, and watcher worker thread names included hashes derived from the selected root. Operational events were also inconsistent free-form sentences rather than stable fields.

## Decision

Emit application-owned console diagnostics as stable `event=... key=value` messages. Approved values are bounded counters, job identifiers, fixed operation names, enums, and exception class names. Never pass paths, queries, extracted text, parser media metadata/reasons, exception messages, request bodies, or throwable objects to a logger.

Record safe startup/shutdown, scan lifecycle, discovery categories, extraction failure status, index commit, watcher uncertainty, database migration, and platform-action events. Use fixed watcher worker thread names rather than path-derived data. Suppress Spring's detailed startup INFO and set third-party logging to WARN by default, while disabling direct Flyway and document-parser logging. A custom Flyway migration strategy emits only a safe success/failure event and replaces migration exceptions with a generic exception without the sensitive original cause.

Persisted scan history remains separate from logging: it intentionally stores local paths and bounded failure messages in the disclosed local SQLite database so the user can understand indexing state.

## Alternatives considered

- Log full paths and exception stacks for easier support: rejected because console capture, screenshots, and copied diagnostics could expose private local data.
- Hash paths in logs: rejected because stable hashes still correlate activity and can be guessed for known paths; the worker-name hashes were removed.
- Disable every log: rejected because lifecycle, failure category, and job correlation are useful without recording private values.
- Keep raw migration causes: rejected for the default build because JDBC URLs contain the local database path. A future explicit diagnostics-export feature may collect more detail with clear consent and redaction.

## Consequences

Default logs are lower-risk to copy and retain, and stable event names support troubleshooting without a logging backend or network service. Some failures provide less root-cause detail; developers must reproduce them locally or add a future explicit, disclosed diagnostics mode rather than weakening the default. Runtime logs remain console-only and are never uploaded by DeepFind.
