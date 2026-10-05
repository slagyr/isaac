---
# isaac-ka10
title: Episodes runs as a context mode plus a session observer
status: draft
type: feature
priority: normal
created_at: 2026-10-05T01:58:10Z
updated_at: 2026-10-05T01:58:10Z
blocked_by:
    - isaac-c52a
---

Micah, 2026-10-04. Follows the context-mode + observer berths bean. Moves episodes off SessionPolicy.

## Shape
- isaac-episodes contributes an `:episodes` **context mode** (cold open at episode boundaries, lineage + continuation seeding, compaction splice) declaring `:requires {:observers #{:episodes}}`, and an `:episodes` **observer** (episode open/seal, scene feeding, turn-marker bookkeeping, index rows).
- The episodes context mode waits for the episodes observer to catch up on the session before a cold open.
- Observer without the context mode is valid: a crew can run `:observers [:episodes]` with `:context-mode :full` or `:reset` and still produce scenes/episodes (for vault sync, search, recall tools), just not use them for its own context.
- Episodes reads the session store directly (no `chronicle-transcript` through a policy). The `:session-policy` field in episodes index rows goes away.
- **Migration is manual** (Micah 2026-10-04: no legacy awareness in code). At deploy, crews on `:session-policy :episodes` are hand-edited to `:context-mode :episodes` + `:observers [:episodes]`; `:session-policy` keys and session stamps are removed by hand.

## Acceptance (scenarios TBD, gated)
- Existing episodes scenarios (cold open, lineage, continuation from isaac-mwqs, recall) pass on the new berths.
- A crew with the episodes observer and `:context-mode :full` produces scenes and episodes, and its prompts carry no cold-open/lineage block.
- A crew with `:context-mode :episodes` and no episodes observer fails config validation naming both.
- A per-turn `--with-context-mode :full` on an episodes session builds full context and the observer keeps recording.
- Handbook (episodes chapter) rewritten for the two settings.
- `bb ci` green; pins coherent with the agent bean's sha.

Likely repo scope: isaac-episodes (+ zanebot/yopp config at deploy).

## Baseline plan (2026-10-04)
Scenarios signed off (draft: features/episodes/context_mode_and_observer.feature, 5 scenarios). Baseline waits for isaac-mwqs to complete: its baselined continuation.feature carries a `session-policy` Background row. At baseline, the planner also rewrites the `session-policy` rows in the 11 other episodes feature files (~75 scenarios; layout.feature asserts the index field itself) to `:context-mode :episodes` + `:observers [:episodes]`, marks the touched scenarios @wip, and includes them in the baseline.
