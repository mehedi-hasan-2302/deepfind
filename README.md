# DeepFind

> You remember what was in the file. DeepFind finds where you put it.

DeepFind is a private, offline-first desktop search application for finding files by name, path, and—later in the MVP—the text inside supported documents. The repository is currently in **Phase 0: Repository Foundation**; indexing and search behavior have not been implemented yet.

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

The backend listens only on `http://127.0.0.1:8080`. The frontend development server listens on `http://127.0.0.1:5173`.

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

The metadata search index defaults to `${user.home}/.deepfind/index`. Set `DEEPFIND_DATA_DIRECTORY` to override the parent directory. Database, settings, and application-log locations will be documented when those stores are introduced.

## Documentation

- [Product requirements](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy](docs/PRIVACY.md)
- [Progress and handoff](docs/PROGRESS.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture decisions](docs/decisions/README.md)
