# 0014 — Use a recursive, reconciliation-aware filesystem watcher

## Context

Full scans make the index correct at a point in time but do not keep results current after files are created, changed, renamed, or deleted. Phase 4 needs a low-latency event source without assuming that operating-system watcher streams are complete or durable. DeepFind must also preserve discovery's exclusion and symbolic-link boundaries and avoid introducing an unbounded in-memory queue.

## Decision

Use Java NIO `WatchService` behind `RecursiveFileWatcher`. A watch session validates one real directory root, registers every eligible existing directory recursively, and registers newly created directory trees as events arrive. It applies the same `ExclusionPolicy` as full discovery and does not follow directory symbolic links.

Publish immutable events with normalized absolute paths and stable kinds: `CREATED`, `MODIFIED`, `DELETED`, and `OVERFLOW`. An overflow names the watched directory whose detailed changes may have been lost. Publish safe categorized failures for inaccessible or disappearing directories without leaking exception text.

Give each session one daemon thread and invoke its observer synchronously. This supplies natural backpressure without adding an unbounded application queue. If the root registration becomes invalid, report root loss and stop the session. Session close is idempotent, closes the native watch service, interrupts the worker, and waits for a bounded interval.

Treat events as hints rather than a durable log. The next application-layer module will coalesce redundant changes, translate them into Lucene mutations, and request scoped or full reconciliation after overflow or watcher failure. Periodic reconciliation remains the final consistency mechanism.

## Alternatives considered

- Poll every file on a short interval: rejected because repeated whole-tree metadata reads scale poorly and increase idle disk activity.
- Add an unbounded asynchronous event queue: rejected because a burst could consume arbitrary memory and still would not repair native overflow.
- Use platform-specific native libraries immediately: deferred because Java NIO provides a portable boundary sufficient for the initial implementation and can be replaced behind the session contract.
- Follow directory symbolic links: rejected because cycles, duplicate trees, and unexpected scope expansion conflict with the discovery policy.

## Consequences

DeepFind now has portable recursive change detection with explicit lifecycle, exclusions, normalized paths, and overflow visibility. Native provider semantics still permit duplicates, coalescing, reordering, and missed details, so consumers must be idempotent and reconciliation remains mandatory. One watcher thread per active root is acceptable for the initial single-root product but should be revisited if multi-root watching becomes common.
