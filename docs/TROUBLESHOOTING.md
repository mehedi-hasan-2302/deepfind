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

- If startup reports a Lucene schema mismatch after upgrading from schema version 1, stop DeepFind and remove only the local `<data-directory>\index` directory, then index the selected folder again. Automated rebuild controls are not implemented yet; never remove the source folder.
- Unsupported, oversized, unreadable, disguised, or malformed documents remain filename/path searchable when their metadata can be read, but their contents are not searchable.
- Content extraction defaults to 20 MiB, 500,000 characters, and 15 seconds per file. Review `deepfind.extraction.*` settings if a legitimate local document is skipped.
- Scanned/image-only PDFs require future local OCR support and normally provide no searchable text today.
