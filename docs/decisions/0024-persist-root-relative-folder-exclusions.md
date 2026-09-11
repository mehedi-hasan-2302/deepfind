# 0024 — Persist root-relative folder exclusions

## Context

The built-in generated-folder exclusions are safe but cannot represent personal archives, private subtrees, or other folders a user does not want indexed. Requiring backend configuration would make this inaccessible to non-technical users. Scanner-only settings would also be unsafe because live watcher events could add excluded entries again.

## Decision

Persist a complete user-managed exclusion list per normalized selected root in SQLite application settings. Accept only non-empty relative paths inside that root, normalize either slash style to the host separator, deduplicate by normalized absolute search key, and bound the list to 100 paths of at most 500 characters. Reject absolute paths, the root itself, control characters, invalid paths, and traversal outside the selected root.

Combine persisted paths with the immutable built-in segment exclusions. Resolve one policy at the start of every full scan, reconciliation run, and watcher session. Replacing the list requires an idle job lane and immediately starts changed-only reconciliation under the new policy. The reconciliation result removes newly excluded index documents and restores newly included eligible entries without modifying the filesystem.

Expose the list through loopback-only `GET /api/index/exclusions` and replace it through validated `PUT /api/index/exclusions`. Present one relative path per line in the indexing panel and explain that saving refreshes only the local index.

## Alternatives considered

- Store arbitrary absolute paths: rejected because settings would be less portable and could silently point outside the selected root.
- Apply exclusions only during full discovery: rejected because reconciliation or watcher events could reintroduce excluded entries.
- Delete excluded source folders: rejected because DeepFind indexing controls must never mutate user content.
- Keep settings only in browser storage: rejected because backend jobs and startup-restored watchers need one authoritative policy.

## Consequences

Every root retains its own local list and the built-in noise exclusions cannot be accidentally disabled. A settings change costs one reconciliation traversal, but unchanged eligible documents avoid re-extraction. Exclusions are path based, so renaming an excluded folder changes whether the rule matches. Settings remain unencrypted with the rest of DeepFind's documented local state.
