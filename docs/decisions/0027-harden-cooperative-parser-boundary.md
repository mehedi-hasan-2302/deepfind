# 0027 — Harden the cooperative parser boundary

## Context

DeepFind parses untrusted local documents with Apache Tika. Tika explicitly is not a security boundary, and the current parser runs inside the backend process. A request deadline limits caller wait time, but cancellation remains cooperative: a parser that ignores interruption can keep its worker occupied. A cancelled task waiting behind that worker can also retain bounded-queue capacity unless it is removed.

Filesystem state may change between discovery and extraction. A direct extractor caller could otherwise provide a directory or symbolic link with a supported extension, and normal permission failures must remain categorized without elevating the whole application.

## Decision

Retain Apache Tika 3.3.2, the current supported 3.x release, and the selected parser-module set for this hardening step. Moving to Tika 4 and its forked-process facilities is an architectural and packaging change, not a safe dependency-only update. Review that option during desktop packaging or earlier if adversarial testing demonstrates the need.

Read basic file attributes with `NOFOLLOW_LINKS` before parser work is submitted and again inside the parser worker. Accept only regular files, enforce the byte limit in both checks, and classify access denial, disappearance, non-regular input, and oversize input with stable outcomes. The second check narrows the discovery-to-parser race but does not claim an atomic filesystem snapshot.

On deadline expiry or caller interruption, cancel the future and purge cancelled work from the parser queue. Preserve the caller's interrupt status. During extractor shutdown, interrupt active work, cancel drained queued futures, purge the queue, and wait for at most the smaller of the extraction deadline or one second. New requests after shutdown receive the stable `EXTRACTOR_SHUTDOWN` reason.

Keep embedded-document extraction disabled and maintain an adversarial XML regression proving that Tika does not resolve a local external entity into extracted text. Run DeepFind with the user's normal permissions. Inaccessible files receive a structured permission result; the application does not request administrator access or imply that it can bypass operating-system controls.

References: [Apache Tika downloads](https://tika.apache.org/download.html), [Apache Tika security model](https://tika.apache.org/security-model.html), and [Apache Tika security advisories](https://tika.apache.org/security.html).

## Alternatives considered

- Treat a deadline as hard termination: rejected because `Future.cancel(true)` cannot forcibly stop Java code that ignores interruption.
- Follow symbolic links supplied directly to the extractor: rejected because it weakens the file identity established by discovery and can cross the intended indexing boundary.
- Run the application as administrator to reach protected paths: rejected because it unnecessarily expands the impact of the entire application and every in-process parser.
- Migrate directly to Tika 4 forked processing: deferred because it changes runtime supervision, protocol, failure recovery, distribution size, and desktop packaging. It requires a dedicated implementation and validation step.

## Consequences

Static symbolic links and non-regular inputs do not reach Tika. Oversized or replaced inputs are checked at two execution boundaries. Cancelled queued tasks no longer consume queue slots behind a non-cooperative parser, caller interruption remains visible, and shutdown has a bounded cooperative wait.

The parser still shares the backend process and its memory. A parser that ignores interruption can retain one of the bounded workers until it returns, and a parser crash or excessive native/process-wide resource use is not isolated. A same-user process can still replace a path after the worker's final attribute check. Process isolation, operating-system sandboxing, and resource enforcement remain candidates for Phase 8 rather than claims of the current implementation.
