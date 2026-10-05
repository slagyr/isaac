---
# isaac-ka10
title: Episodes runs as a context mode plus a witness
status: draft
type: feature
priority: normal
created_at: 2026-10-05T01:58:10Z
updated_at: 2026-10-05T01:58:10Z
blocked_by:
    - isaac-c52a
---

Micah, 2026-10-04. Follows the context-mode + witness berths bean. Moves episodes off SessionPolicy.

## Shape
- isaac-episodes contributes an `:episodes` **context mode** (cold open at episode boundaries, lineage + continuation seeding, compaction splice) declaring `:requires {:witnesses #{:episodes}}`, and an `:episodes` **witness** (episode open/seal, scene feeding, turn-marker bookkeeping, index rows).
- The episodes context mode waits for the episodes witness to catch up on the session before a cold open.
- Witness without the context mode is valid: a crew can run `:witnesses [:episodes]` with `:context-mode :full` or `:reset` and still produce scenes/episodes (for vault sync, search, recall tools), just not use them for its own context.
- Episodes reads the session store directly (no `chronicle-transcript` through a policy). The `:session-policy` field in episodes index rows goes away.
- **Migration**: `:session-policy :episodes` → `:context-mode :episodes` + `:witnesses [:episodes]`; `:session-policy :chronicle` (or absent) → nothing. Sessions stamped `:session-policy` lose the stamp. Decide in scenarios whether this is a config-load upgrade (with a warning from `isaac config …`) or a one-time `modules setup`/script; zanebot and yopp crews get migrated by hand at deploy either way.

## Acceptance (scenarios TBD, gated)
- Existing episodes scenarios (cold open, lineage, continuation from isaac-mwqs, recall) pass on the new berths.
- A crew with the episodes witness and `:context-mode :full` produces scenes and episodes, and its prompts carry no cold-open/lineage block.
- A crew with `:context-mode :episodes` and no episodes witness fails config validation naming both.
- A per-turn `--with-context-mode :full` on an episodes session builds full context and the witness keeps recording.
- Migration of a `:session-policy :episodes` crew per the chosen path.
- Handbook (episodes chapter) rewritten for the two settings.
- `bb ci` green; pins coherent with the agent bean's sha.

Likely repo scope: isaac-episodes (+ zanebot/yopp config at deploy).
