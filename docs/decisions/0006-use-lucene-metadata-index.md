# 0006 — Use Lucene for persistent metadata search

## Context

DeepFind must answer filename and path queries from a persistent local index rather than scan the filesystem for every search. The schema must support exact identity, relevance-ranked text matching, future filters/sorting, and safe version upgrades.

## Decision

Use Apache Lucene 10.5.1 with `FSDirectory`, a delimiter-aware lowercase metadata analyzer, one `IndexWriter`, and `SearcherManager` for near-real-time readers. The analyzer splits dots, dashes, underscores, path separators, number transitions, and case transitions while preserving original terms, allowing remembered filename fragments to match natural queries. The index schema version is stored in Lucene commit metadata and a mismatch fails explicitly so a future rebuild workflow can handle it safely.

The normalized absolute path is the unique update/delete key. Metadata discovery streams directly into `updateDocument`; repeated scans replace matching documents rather than create duplicates. Search input is trimmed and escaped before a multi-field parser sees it, so MVP plain search does not expose Lucene query syntax.

### Field model

| Field | Tokenized | Stored | Exact/filter | Sort | Purpose |
| --- | --- | --- | --- | --- | --- |
| `pathKey` | No | Yes | Yes | No | Stable update/delete key |
| `absolutePath` | No | Yes | No | No | Reconstruct result path |
| `filename` | Yes | Yes | No | No | Filename relevance search |
| `filenameExact` | No | No | Yes | No | Exact and prefix boosts |
| `pathText` | Yes | No | No | No | Directory/path search |
| `extension` | No | Yes | Yes | No | Result metadata and future filter |
| `kind` | No | Yes | Yes | No | File/directory/link filter |
| `sizeBytes` | No | Yes | LongPoint | Numeric doc values | Range filter and future sort |
| `modifiedAt` | No | Yes | LongPoint | Numeric doc values | Range filter and future sort |
| `createdAt` | No | Yes | LongPoint | Numeric doc values | Range filter and future sort |
| `lastIndexedAt` | No | Yes | LongPoint | Numeric doc values | Diagnostics and reconciliation |

Exact filename clauses receive the strongest boost, then filename prefixes, tokenized filenames, and paths. Search results expose a categorical match reason, not Lucene's raw score.

## Alternatives considered

- SQL `LIKE` queries: rejected because they do not provide the required scalable full-text relevance behavior.
- Elasticsearch: rejected because a separate service is inappropriate for a local desktop modular monolith.
- Reopen a fresh reader for every search: rejected because `SearcherManager` provides controlled near-real-time reader refresh and reuse.
- Store only display fields: rejected because numeric point/doc-value fields are needed for later size/date filters and sorting without another schema migration.

## Consequences

Lucene becomes the authoritative search index while SQLite will later hold roots, settings, jobs, and failure records. Search sees uncommitted updates after a reader refresh, while explicit commits provide restart durability. Index files must be closed carefully on Windows. Incompatible future schemas require a user-visible rebuild path rather than silent deletion.
