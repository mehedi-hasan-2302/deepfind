# 0001 — Modular monolith with Spring Boot

## Context

DeepFind needs filesystem discovery, extraction, indexing, search, persistence, jobs, and platform integration on one computer without operational infrastructure.

## Decision

Use a Java 21 Spring Boot modular monolith. Keep package boundaries explicit and isolate infrastructure-specific code behind application contracts.

## Alternatives considered

- Microservices: rejected because local deployment and lifecycle complexity provide no MVP benefit.
- A JavaScript-only backend: rejected because the authoritative specification selects Spring Boot and the Java ecosystem fits Lucene and Tika directly.

## Consequences

The application has one backend process and build. Architectural discipline must be enforced through packages and tests rather than network boundaries.
