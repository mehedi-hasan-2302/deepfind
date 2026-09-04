# Architecture

## Status

Phase 0 establishes the development boundary. Indexing, persistence, and desktop packaging are not implemented yet.

## Components

- `frontend`: React and TypeScript interface served by Vite during development.
- `backend`: Java 21 Spring Boot modular monolith exposing a local HTTP API.
- Future infrastructure adapters: Lucene for full-text search, SQLite for structured application state, Apache Tika for bounded extraction, and platform adapters for open/reveal actions.
- Future desktop shell: responsible for starting the backend, waiting for health, hosting the UI, and shutting down cleanly.

## Dependency direction

```text
API/UI -> application services -> domain contracts -> infrastructure adapters
```

Controllers will not operate Lucene readers, Tika parsers, databases, or filesystem walkers directly.

## Filesystem discovery

Discovery uses Java NIO `walkFileTree` and emits immutable metadata, progress snapshots, and bounded failure descriptions through an observer. It does not retain the discovered tree in memory. Directory symlinks are indexed as link entries but are not followed. A small default exclusion set removes common generated trees; explicit exclusions can target an absolute subtree. See ADR 0005.

## Data flow

The intended indexing pipeline is discovery → bounded metadata queue → metadata index → bounded extraction workers → content update → progress event. Metadata should become searchable before slower content extraction completes.

The intended search pipeline is query parsing → normalization and filters → Lucene query → ranking → snippets → API DTOs.

## Concurrency

Discovery, extraction, index writing, and search will use separate bounded execution resources. Backpressure is mandatory; millions of discovered paths must not accumulate in memory.

## Storage roles

- Lucene: authoritative full-text index for filename, path, metadata, and extracted content.
- SQLite: indexed roots, exclusions, settings, jobs, histories, and failure records.
- Filesystem: read-only source data. DeepFind does not modify indexed files.

## Security boundary

The backend binds to `127.0.0.1`, never `0.0.0.0`, by default. The initial health API exposes no file data. Future filesystem actions must validate input and avoid arbitrary content-read endpoints.

## Index model

The detailed Lucene field model and schema version will be committed when Lucene is introduced in Phase 1. The normalized absolute path is the likely MVP stable key, with platform-aware case handling.

## Platform integration

Open and reveal actions will live behind a platform abstraction. Windows is the first development target, while path handling will use Java `Path` APIs to preserve later macOS/Linux support.
