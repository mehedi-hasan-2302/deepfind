# 0002 — React and TypeScript frontend

## Context

The desktop interface needs accessible components, predictable state domains, component tests, and compatibility with a later desktop shell.

## Decision

Use React 19 with TypeScript and Vite. Keep state local until shared state is justified by Phase 1 behavior.

## Alternatives considered

- Vue and Svelte: both viable, but selecting React avoids prolonged framework comparison and has strong testing and desktop-shell support.
- Plain JavaScript: rejected because typed API contracts reduce integration mistakes.

## Consequences

Node tooling is required for development. The runtime UI remains a static bundle suitable for a desktop webview.
