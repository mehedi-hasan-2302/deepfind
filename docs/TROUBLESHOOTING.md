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
- Filesystem watcher tests use the host's native Java watch provider. Symbolic-link coverage is skipped when the current Windows account cannot create links. Provider overflow requests automatic reconciliation; the scheduler retries once the single indexing worker is idle.
- Incremental event buffering defaults to 256 entries. If diagnostics later show sustained watcher overflow on a very active tree, increasing `deepfind.watcher.queue-capacity` can absorb a larger burst but uses more memory; automatic reconciliation remains the correctness backstop.
- Automatic reconciliation starts after 30 seconds and normally runs every 15 minutes. It may traverse the full selected tree, but unchanged files are not re-extracted. Use `deepfind.reconciliation.*` settings to adjust the cadence during development; an excessively short interval can create unnecessary filesystem work.
- **Live updates active** means native changes are being tracked. **Index repair pending** means an event history became uncertain and automatic reconciliation will retry when the indexing worker is free. **Live updates unavailable** means the selected folder could not be watched; restore access and use **Refresh index**, or wait for automatic retry.
- **Refresh index** reconciles the persisted selected folder and is disabled while another indexing job is running or while the path field differs from that selected folder. Use **Start indexing** after changing the path.
- **Pause indexing** may briefly show **Pausing** while the current file and bounded extraction work finish. A paused job has committed its completed work and live watching resumes. **Resume indexing** deliberately starts changed-only reconciliation with a new job ID; it does not continue from the displayed path because files earlier in the traversal may have changed.
- **Folders to skip** uses paths relative to the selected root, one per line (for example `Private` or `Archive\Old`). Absolute paths, the root itself, and `..` paths that escape the root are rejected. Saving starts a refresh; wait for it to finish before editing the list again. Clearing the field and saving re-includes eligible folders. This only changes the local index and never deletes source data.
- Use balanced double quotes for an exact analyzed phrase, such as `"salary expectation"`. If a closing quote is missing, DeepFind intentionally searches the words as ordinary text rather than rejecting the request. Phrase search is lexical and does not yet add synonyms or semantic similarity.
- **Similar filename** means the ordinary search returned no results and DeepFind retried one plain 4–32-character word against filename tokens only. This conservative fallback does not run for short, multi-word, quoted, path, or content similarity searches.
- Entry type, extension, modified-time, and size filters combine with the text query. Use **Clear filters** when an expected result disappears. Extensions may be entered as `pdf` or `.pdf` and are matched case-insensitively. Size presets use binary units (1 MB = 1,048,576 bytes), and modified presets compare against the current browser time.
- **Load more** appends the next 50 ranked results while preserving the active query and filters. Because the local index remains live, filesystem changes between page requests can shift offsets; starting a fresh search gives the newest coherent ordering. API offsets above 10,000 are intentionally rejected to bound Lucene collection work.

## Local database issues

- If startup reports a Flyway migration validation error, do not delete the database. Stop DeepFind, copy `<data-directory>\deepfind.db` and any adjacent `-wal`/`-shm` files as a backup, then use a build compatible with that database or investigate the migration checksum change.
- If startup reports SQLite corruption, DeepFind intentionally leaves the file in place. Stop the application and preserve `<data-directory>\deepfind.db` before attempting recovery. Moving only that database aside creates fresh settings on the next start but loses saved-root records and timestamps; it does not remove the Lucene index or any source files.
- If the interface reports that the previous indexing run was interrupted, existing committed search results remain usable. Select **Start indexing** again to perform a full reconciliation scan of the restored folder. DeepFind does not claim to resume from the exact interrupted filesystem position yet.
- A deliberately paused job remains **Paused** after an application restart and can be resumed. A crash while **Pausing** is classified as **Interrupted**, because DeepFind cannot prove that the safe commit boundary completed.
