# 0010 — Add extracted content to Lucene schema version 2

## Context

DeepFind's first Lucene schema searches filenames and paths only. Phase 2 must make supported document terms searchable without delaying every metadata upsert, duplicating entries, persisting retrievable full text unnecessarily, or allowing an unbounded extraction backlog.

## Decision

Advance the Lucene schema to version 2. Add an indexed, non-stored `content` field and stored exact `extractionStatus` plus stable `extractionReason` fields. Keep normalized absolute path as the unique update key. Write metadata first, then replace that same document with its metadata and extraction outcome because Lucene document updates replace whole documents rather than patching fields.

Feed extraction through a fixed content-indexing pool with a bounded queue. Use caller-runs rejection behavior so filesystem discovery slows when extraction reaches capacity. The existing bounded extractor independently controls parser workers, output, file size, queue capacity, and deadlines. Wait for accepted content work before the indexing job's final commit and completed state.

Search filename, path, and content together. Preserve exact filename and filename-prefix boosts, weight general filename matches above path matches, and weight content lowest. Classify results as exact filename, filename prefix, filename, path, or content so the UI can explain why each file matched.

Do not store full extracted text as a retrievable Lucene field in this step. Content snippets will use a separately designed bounded representation in the next module. Reject version 1 indexes explicitly until user-facing rebuild management is implemented.

## Alternatives considered

- Store content only in SQLite: rejected because Lucene is the authoritative full-text index and SQL substring search does not meet the scale goal.
- Store full extracted text in Lucene immediately: deferred because snippets need a deliberate size and privacy policy rather than an unlimited stored field.
- Finish all extraction before metadata indexing: rejected because filename/path results should become available before slower parsers finish.
- Accumulate extraction futures for the entire scan: rejected because memory would grow with the number of files.
- Migrate version 1 documents in place: rejected because their source content must be re-read anyway; a clean rebuild is simpler and more reliable at this stage.

## Consequences

Terms that exist only inside supported documents are searchable and durable across restart. Re-indexing changed content replaces old terms without duplicates. Malformed or unsupported content does not remove searchable metadata. The local index now contains sensitive document terms even though full text is not a stored field. Existing schema version 1 indexes must be rebuilt, and content snippets remain future work.
