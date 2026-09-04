# Product Requirements

## Problem

People often remember what a file contained but not its filename or location. DeepFind creates a persistent local index so they can find that file without manually browsing folders or relying on inconsistent operating-system content search.

## Target users

DeepFind serves non-technical and technical users with years of files spread across computers, disks, downloads, projects, and backups.

## Value proposition

Find files quickly by name, path, metadata, and supported document content while keeping files, queries, and the search index on the user's device.

## MVP

- Choose folders or drives to index.
- Search file and folder names and paths.
- Search supported document contents with useful snippets.
- Open a result, reveal it in the platform file manager, or copy its path.
- See honest indexing progress and actionable failures.
- Preserve the index across restarts and reconcile filesystem changes.
- Manage exclusions and run entirely without an account or cloud service.

## Non-goals

The MVP does not include accounts, cloud sync, remote search, OCR, semantic search, document chat, destructive file management, telemetry, or online AI APIs.

## UX principles

The application should be immediately understandable, keyboard accessible, transparent about indexing, explicit about why results match, and safe around user files.

## Roadmap

Development follows Phases 0–10 in the master build specification. OCR, local semantic retrieval, archive search, saved searches, and tagging remain future candidates only after ordinary search is dependable.
