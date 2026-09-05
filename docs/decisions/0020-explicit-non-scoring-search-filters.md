# 0020 — Keep search filters explicit and non-scoring

## Context

Users need to narrow broad lexical searches by indexed metadata. Embedding filter syntax such as `size:>100mb` in the search box would require a larger query language, create ambiguity with real filenames, and mix candidate eligibility with relevance. The Lucene schema already contains exact kind and extension fields plus point-indexed size and timestamps.

## Decision

Accept optional typed parameters on `GET /api/search`: entry kind, extension, modified-after/before instants, and minimum/maximum byte sizes. Normalize a leading extension dot and case, validate individual values at the controller boundary, and reject reversed ranges before reaching Lucene. Map malformed enum, instant, pattern, and range values to the existing stable `INVALID_REQUEST` response.

Compose valid metadata constraints as Lucene `FILTER` clauses around the existing text query. Use exact terms for kind and extension and inclusive `LongPoint` ranges for modified time and size. Do not let filters contribute to document scores, so filename-first and phrase ranking are preserved among eligible results.

Expose compact entry-type, extension, recent-modification, and size controls in the frontend. Debounce filter changes through the existing cancellable search lifecycle and provide one action to clear every filter. Size presets use binary megabytes; recent-date presets produce explicit ISO-8601 instants.

## Alternatives considered

- Parse filters from search text: deferred because a mini-language adds escaping, error-recovery, and filename ambiguity before it is needed.
- Filter returned pages in the browser: rejected because totals and pagination would be incorrect and disallowed results would still consume backend limits.
- Add filter values as scoring clauses: rejected because metadata restrictions should not reorder otherwise identical text results.
- Add separate endpoints per filter: rejected because one composable search contract is simpler and supports combinations.

## Consequences

Filtering is predictable, composable, and efficient without changing existing unfiltered results. API clients can use precise instants and bytes, while the current UI provides approachable presets. Natural-language dates, saved filters, richer file-type groups, and query-text filter syntax remain optional later enhancements.
