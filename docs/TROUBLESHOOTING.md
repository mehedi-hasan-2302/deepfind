# Troubleshooting

## Backend does not start

- Confirm Java 21 with `java -version`.
- Run `backend\mvnw.cmd test` to distinguish compilation/test failures from port conflicts.
- If port 8080 is occupied, stop the conflicting local process before retrying. Dynamic port selection will be added with desktop packaging.

## Frontend does not start

- Confirm Node.js 22.12 or newer with `node --version`.
- Run `npm ci` in `frontend`, then `npm run check`.
- Confirm port 5173 is available.

## Indexing and search issues

- If startup reports a Lucene schema mismatch after upgrading from an earlier schema, stop DeepFind and remove only the local `<data-directory>\index` directory, then index the selected folder again. Automated rebuild controls are not implemented yet; never remove the source folder.
- Unsupported, oversized, unreadable, disguised, or malformed documents remain filename/path searchable when their metadata can be read, but their contents are not searchable.
- Content extraction defaults to 20 MiB, 500,000 characters, and 15 seconds per file. Review `deepfind.extraction.*` settings if a legitimate local document is skipped.
- Scanned/image-only PDFs require future local OCR support and normally provide no searchable text today.
- Filesystem watcher tests use the host's native Java watch provider. Symbolic-link coverage is skipped when the current Windows account cannot create links. The watcher exposes provider overflow as a reconciliation signal; automatic index repair is part of the remaining Phase 4 work.
- Incremental event buffering defaults to 256 entries. If diagnostics later show sustained watcher overflow on a very active tree, increasing `deepfind.watcher.queue-capacity` can absorb a larger burst but uses more memory; reconciliation is still required for correctness.

## Local database issues

- If startup reports a Flyway migration validation error, do not delete the database. Stop DeepFind, copy `<data-directory>\deepfind.db` and any adjacent `-wal`/`-shm` files as a backup, then use a build compatible with that database or investigate the migration checksum change.
- If startup reports SQLite corruption, DeepFind intentionally leaves the file in place. Stop the application and preserve `<data-directory>\deepfind.db` before attempting recovery. Moving only that database aside creates fresh settings on the next start but loses saved-root records and timestamps; it does not remove the Lucene index or any source files.
- If the interface reports that the previous indexing run was interrupted, existing committed search results remain usable. Select **Start indexing** again to perform a full reconciliation scan of the restored folder. DeepFind does not claim to resume from the exact interrupted filesystem position yet.
