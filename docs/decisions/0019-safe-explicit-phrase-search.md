# 0019 — Add safe explicit phrase search

## Context

Plain multi-term search requires all analyzed terms but does not distinguish adjacency. Users who remember a phrase inside a document need a precise way to remove results where the words are scattered. Passing raw user text into Lucene query syntax would introduce operators, parser errors, and an unnecessarily complicated query language.

## Decision

Recognize only balanced double-quoted segments as explicit phrases. Treat text outside quotes as ordinary required terms, and require every quoted phrase and ordinary component to match. Construct phrase queries programmatically with the existing analyzer across filename, path, and content fields. Keep filename phrase boosts above path and content phrase boosts, and classify content phrase results as `EXACT_PHRASE` without exposing raw Lucene scores.

Strip quote delimiters only for literal filename comparison and result explanation. If quotes are unmatched, remove the delimiter and interpret the complete input as ordinary text. Empty quoted input returns no results. Continue escaping ordinary text before the classic query parser sees it.

## Alternatives considered

- Expose Lucene query syntax directly: rejected because local users should not need a query language and malformed operators must not cause failures.
- Treat every multi-word query as a phrase: rejected because normal search should continue finding terms that are separated or distributed across fields.
- Implement phrase matching as a post-search string filter: rejected because it would produce incomplete pages and discard Lucene positional indexing.
- Add fuzzy phrase behavior now: rejected because typo tolerance requires separate bounded relevance rules.

## Consequences

Users can request predictable adjacent-term matching in filenames, paths, and supported document content while retaining graceful plain-text fallback. Exact phrase results are explainable in the UI and filename-first relevance remains intact. The syntax intentionally does not support escaped quotes, field operators, proximity slop, or nested expressions; filters and bounded fuzzy matching remain later Phase 5 work.
