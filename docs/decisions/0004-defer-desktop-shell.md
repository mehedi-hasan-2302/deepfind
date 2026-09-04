# 0004 — Defer desktop-shell selection

## Context

Tauri and Electron can both package the UI and manage a bundled backend, but Phase 0 does not yet exercise lifecycle, updater, installer, or platform-action requirements.

## Decision

Keep the frontend a shell-neutral Vite application and defer the packaging choice until Phase 8 or an earlier proven need.

## Alternatives considered

- Select Tauri now: attractive for footprint, but premature before lifecycle and runtime-bundling tests.
- Select Electron now: mature process control, but adds runtime size before packaging work begins.

## Consequences

Development uses separate frontend/backend processes. Code must avoid browser assumptions that would prevent either desktop shell later.
