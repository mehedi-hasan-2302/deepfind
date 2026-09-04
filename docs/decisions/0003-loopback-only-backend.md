# 0003 — Loopback-only local backend

## Context

The backend will handle private filesystem metadata and content. Binding to every network interface would expose an unnecessary LAN attack surface.

## Decision

Bind Spring Boot to `127.0.0.1` by default. Development frontend hosting also uses loopback. Future desktop integration must authenticate or otherwise constrain sensitive local API actions where appropriate.

## Alternatives considered

- Bind to `0.0.0.0`: rejected because DeepFind is not a remote service.
- Embed all backend calls directly in the desktop shell: deferred because the specified Spring Boot boundary enables clear modules and tests.

## Consequences

Other devices cannot reach DeepFind by default. A future dynamic port and desktop lifecycle handshake must preserve this boundary.
