# DeepFind

> You remember what was in the file. DeepFind finds where you put it.

DeepFind is a private, offline-first desktop search application for finding files by name, path, and the text inside supported documents. **Phase 1: Basic File Metadata Search is complete, and Phase 2 is in progress.** Users can select a local folder by path, monitor indexing, search filenames, paths, text, Markdown, common source files, PDF, and DOCX, then open, reveal, or copy result paths from the web interface.

## Privacy baseline

DeepFind is designed to process files locally. Core functionality will not require an account, cloud API, telemetry, or document uploads. The local backend binds to `127.0.0.1` by default.

## Repository layout

```text
backend/      Spring Boot local API and future indexing engine
frontend/     React + TypeScript user interface
docs/         Product, architecture, privacy, progress, and decisions
```

## Prerequisites

- Java 21
- Node.js 22.12 or newer
- npm 11 or newer

Maven does not need to be installed globally; use the checked-in Maven Wrapper.

## Run locally

Backend (PowerShell):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend (a second terminal):

```powershell
cd frontend
npm install
npm run dev
```

The backend listens only on `http://127.0.0.1:8080`. The frontend development server listens on `http://127.0.0.1:5173` and proxies relative `/api` requests to that loopback backend.

## Verify

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm ci
npm run check
```

`npm run check` runs frontend linting, tests, and the production build.

## Packaging

Desktop packaging is intentionally deferred to Phase 8. The production goal is one self-contained desktop application that bundles its required runtime.

## Local data

The search index defaults to `${user.home}/.deepfind/index`. Set `DEEPFIND_DATA_DIRECTORY` to override the parent directory. Database, settings, and application-log locations will be documented when those stores are introduced.

Extracted text is indexed into local Lucene postings but is not retained as a retrievable stored field. Extraction defaults are a 20 MiB file limit, 500,000 extracted characters, a 15-second deadline, two workers, and a queue capacity of 32. Override them with the `deepfind.extraction.*` Spring properties when developing or packaging.

## Documentation

- [Product requirements](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy](docs/PRIVACY.md)
- [Progress and handoff](docs/PROGRESS.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture decisions](docs/decisions/README.md)
