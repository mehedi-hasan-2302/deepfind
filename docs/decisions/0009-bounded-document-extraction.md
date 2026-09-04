# 0009 — Bound and isolate document extraction

## Context

DeepFind must search inside untrusted local documents without letting one large, malformed, or disguised file exhaust memory, monopolize indexing, crash the application, or leak sensitive text. Extension-only classification is insufficient, and coupling indexing directly to a parser would make later hardening difficult.

## Decision

Use Apache Tika 3.3.2 behind DeepFind-owned `ContentExtractor` and `DocumentParser` contracts. Select only the text, code, XML, Microsoft, and PDF parser modules needed for the initial formats instead of Tika's complete standard parser package. The initial policy accepts text, Markdown, common source/configuration files, PDF, and DOCX. HTML and other stretch formats remain deferred.

Require both an allowlisted extension and a compatible Tika-detected media type. Disable embedded-document extraction. Reject oversized files before parsing, cap output with Tika's character write limit, and execute parsing on a fixed-size worker pool with a bounded queue and per-request deadline. Defaults are 20 MiB, 500,000 characters, 15 seconds, two workers, and 32 queued requests; each is configurable through `deepfind.extraction.*`.

Return one structured status: `SUCCESS`, `UNSUPPORTED`, `SKIPPED_TOO_LARGE`, `PERMISSION_DENIED`, `PARSE_ERROR`, or `TIMEOUT`. Public results contain stable reasons rather than raw exception messages, paths, or content.

The current in-process timeout cancels the task through thread interruption. This limits caller wait time and contains concurrency, but it cannot forcibly stop a parser that ignores interruption. Treat process isolation as a later hardening option for risky formats or evidence of non-cooperative parsers.

## Alternatives considered

- Trust file extensions: rejected because renamed executable or malformed content would reach the wrong parser path.
- Use Tika's complete standard parser package: rejected because it adds parsers and transitive libraries for formats outside the MVP policy.
- Parse synchronously on indexing workers: rejected because parser latency and failures would share the core discovery/index-writing execution path.
- Require process isolation immediately: deferred because the initial format set and bounded in-process contract establish the product path with less packaging complexity; the parser abstraction preserves a migration boundary.

## Consequences

Extraction failures are local to one file and have deterministic categories. Memory and concurrency have explicit application limits, and adding a new format requires both policy and dependency review. Cold parser startup contributes to the first extraction's latency. Deadlines remain cooperative until a process-isolated implementation is introduced. This step does not yet persist or search extracted text; Lucene integration follows separately.
