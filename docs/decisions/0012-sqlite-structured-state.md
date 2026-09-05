# 0012 — Use SQLite for structured local state

## Context

Lucene is the authoritative full-text search index, but application state such as selected roots, settings, scan history, and failure records has relational update and migration needs. The first Phase 3 slice must restore the most recently selected root after restart without coupling job orchestration to SQL or changing the Lucene lifecycle.

## Decision

Store structured local state in `<data-directory>/deepfind.db` using Xerial SQLite JDBC. Configure foreign keys, a five-second busy timeout, write-ahead logging, and normal synchronous mode. Use Spring JDBC behind repository contracts and a transactional `RootCatalog` application service.

Manage schema changes with Flyway migrations. Version 1 creates normalized indexed-root records with unique path identity and selection/index timestamps. Version 2 adds generic key/value application settings. Migrations run before persistence-backed services initialize, and invalid or corrupt databases stop startup rather than being deleted or recreated silently.

Persist a valid root selection before submitting its indexing job and record successful completion afterward. Restore the `last_selected_root` setting into the process-local idle job status on startup. Do not persist live progress or infer that interrupted work completed; scan history and explicit resume behavior belong to later Phase 3 steps.

## Alternatives considered

- Store settings in JSON: rejected because later job history and failure records need atomic relational updates and explicit schema evolution.
- Store structured state in Lucene: rejected because Lucene's document index is not an appropriate transactional settings store.
- Use an external database service: rejected because DeepFind must remain self-contained, local-first, and offline-capable.
- Automatically replace a corrupt database: rejected because silent deletion would destroy user state and conceal recovery evidence.

## Consequences

Root selections survive restarts, Unicode paths round-trip, duplicate normalized roots collapse to one record, and schema upgrades are repeatable. SQLite adds a local native-backed JDBC dependency and migration lifecycle. The database contains sensitive local paths and is not encrypted by DeepFind. A corrupt database prevents startup until the user preserves and repairs or deliberately replaces it.
