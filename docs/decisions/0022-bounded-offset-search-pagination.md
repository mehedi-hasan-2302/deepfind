# 0022 — Use bounded offset windows for interactive pagination

## Context

The search API returned only its first limited result set even when `totalHits` indicated more matches. The interface therefore could not explore later results. Lucene cursor pagination can be efficient, but a cursor containing internal document IDs and scores becomes invalid when the live index refreshes or merges and would require snapshot ownership or signed opaque state.

## Decision

Add explicit zero-based `offset` and `limit` request parameters. Keep page size between 1 and 1,000 and cap offset at 10,000 so a request cannot demand unbounded top-hit collection. Return the applied `offset`, `limit`, a `hasMore` continuation flag, and `totalHitsExact` with every response.

Collect at most `offset + limit + 1` scored hits. Slice only the requested window and use the extra hit to derive `hasMore`, independently of whether Lucene's total-hit relation is exact or a lower bound. Preserve the same query, filters, relevance order, and fuzzy-fallback decision for every page.

Request 50 results per page in the frontend. Append a page only when it still belongs to the current query and expected offset, cancel an outstanding pagination request when the query or filters change, disable the control while loading, and remove it when `hasMore` is false. Display a plus suffix when the total is a lower bound.

## Alternatives considered

- Raise the initial limit or return all hits: rejected because response, mapping, rendering, and memory costs would grow with broad searches.
- Use Lucene `searchAfter` document/score cursors immediately: deferred because the actively changing index requires snapshot semantics or explicit cursor invalidation to make those cursors safe.
- Infinite scrolling: rejected for now because an explicit control is accessible, predictable, and avoids accidental work.
- Allow arbitrary offsets: rejected because deep top-hit collection cost grows with the offset even though only one page is returned.

## Consequences

Users can explore broad result sets in bounded increments, and clients can distinguish exact totals from safe lower bounds. Re-running an offset page against a changed live index can produce a shifted window, including a duplicate or omission; restarting the search restores the newest ordering. Cursor snapshots can supersede this decision if evidence shows users need stable traversal beyond the current bound.
