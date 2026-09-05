# 0021 — Bound fuzzy search to zero-result filenames

## Context

A small filename typo can make an otherwise useful lexical search return nothing. Applying fuzzy matching to every field or mixing approximate candidates into normal results would weaken predictable filename-first relevance, make phrase semantics unclear, and allow costly term expansion across extracted content.

## Decision

Run a second Lucene query only when the complete primary query returns zero matches. The retry targets the analyzed filename field only and preserves every explicit metadata filter from the original request. It is eligible only for one unquoted term containing 4–32 Unicode letters or digits.

Allow one edit for terms of four or five characters and two edits for longer terms. Require the first character to match, retain transposition support, and cap Lucene term expansion at 50 candidates. Label every retry result `FUZZY_FILENAME`, expose it as **Similar filename** in the interface, and omit content snippets because content does not participate in this query.

## Alternatives considered

- Mix fuzzy clauses into every primary query: rejected because approximate matches could displace exact path or content results and make ranking harder to explain.
- Fuzz paths and extracted content: rejected because their term dictionaries are much broader and approximate content matches are likely to be noisy and expensive.
- Accept phrases, punctuation, or arbitrarily long terms: rejected because token boundaries and user intent become ambiguous.
- Correct the user's query automatically: rejected because displaying labeled results preserves the original input and makes approximation explicit.

## Consequences

Common single-word filename misspellings gain useful recall while all successful existing searches keep exactly their normal candidate set. Filters continue to restrict approximate results. Typos in multi-word, quoted, path-only, or content-only searches remain unmatched; broader suggestions or semantic search can be added separately if evidence supports them.
