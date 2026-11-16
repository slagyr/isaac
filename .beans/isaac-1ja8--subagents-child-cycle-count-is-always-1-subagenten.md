---
# isaac-1ja8
title: 'Subagents: child cycle count is always 1 (subagent/ended, list, result stats)'
status: draft
type: bug
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:40Z
updated_at: 2026-11-16T14:44:40Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (`tool/subagent.clj`, subagent lifecycle tracking from isaac-urog).

## Observed (zanebot, 2026-11-16)

Every `:subagent/ended` line reported `:cycles 1`, whatever the child did. Tranquil Otter (grok-code) made 18 requests — its own `turn/ended` says `:requests 18` — yet `:subagent/ended` said `:cycles 1`. All six children of that run said 1. The same count feeds `subagent__list` and the stats line in the parent's result message, so the parent is misinformed too.

## Fix

The child's cycle count comes from the child turn's real count (the `turn/ended` numbers), not a placeholder or the first cycle.

## Acceptance

Draft — scenario to come: a child that makes two tool cycles before replying reports `cycles: 2` (or more) in `:subagent/ended`, in `subagent__list`, and in the parent's result message.
