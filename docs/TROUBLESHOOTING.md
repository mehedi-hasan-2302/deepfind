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

Indexing is not implemented in Phase 0. Permission-denied handling, corrupt-document recovery, rebuild controls, parser failures, disk-usage diagnostics, and removable-drive state will be documented as their features are introduced.
