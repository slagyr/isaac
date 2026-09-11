---
# isaac-qkqm
title: 'Compaction logging suite health on main: max-attempt stop + toolCall/toolResult pairing'
status: draft
type: bug
priority: high
created_at: 2026-08-26T07:07:32Z
updated_at: 2026-08-26T07:07:32Z
---

Pre-existing red on isaac-agent origin/main (reported during isaac-pqjn verify handoff narrowing):

`bb features features/session/compaction_logging.feature` fails on detached `origin/main@59de03b` and on a pqjn rebase worktree with the same two scenarios:

1. `compaction stops retrying after max-compaction-attempts consecutive cross-turn failures`
2. `Compaction keeps toolCall and toolResult together`

These failures reproduce independent of pqjn token-accounting work, so they are suite-health / compaction product debt, not pqjn acceptance.

## Scope

isaac-agent compaction behavior / feature health on main. Restore the two named compaction_logging scenarios (or wip/split them honestly if they are themselves over-broad and need dedicated follow-ups).

## Acceptance

```
bb features features/session/compaction_logging.feature
```

0 failures on main for the two named scenarios above (and the rest of the file).

## Notes

- pqjn acceptance must not name this file broadly while it is red on main independent of pqjn.
- Related beans: isaac-pqjn (token accounting), isaac-63f3 (toolCall/toolResult pairing history-offset context), isaac-5cr6 (completed compaction logging scenario), isaac-os7r (summary template).
