---
# isaac-1ja8
title: 'Subagents: child cycle count is always 1 (subagent/ended, list, result stats)'
status: completed
type: bug
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:40Z
updated_at: 2026-11-16T15:47:50Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (`tool/subagent.clj`, subagent lifecycle tracking from isaac-urog).

## Observed (zanebot, 2026-11-16)

Every `:subagent/ended` line reported `:cycles 1`, whatever the child did. Tranquil Otter (grok-code) made 18 requests — its own `turn/ended` says `:requests 18` — yet `:subagent/ended` said `:cycles 1`. All six children of that run said 1. The same count feeds `subagent__list` and the stats line in the parent's result message, so the parent is misinformed too.

## Fix

The child's cycle count comes from the child turn's real count (the `turn/ended` numbers), not a placeholder or the first cycle.

## Acceptance

Scenario in `isaac-agent/features/tool/subagent_control.feature` (scenario-level `@wip`; remove it when it passes):

- `bb features features/tool/subagent_control.feature:104` — a child that reads a file before replying reports `cycles: 2` in `subagent__list`, `:subagent/ended`, and the parent's result stats line

Lead: `deliver!` and the observer both take `(count (:cycle-usages result))` — check what the child turn's result actually carries.

feature-baseline: isaac-agent af7101ebd7321fe96dd4d9c0c90039ffb5c0ff68
feature-blob: isaac-agent features/tool/subagent_control.feature a2b02bda946ac4b187d3729ae71da63f39e50012 104


## Landed on main (2026-11-16)

Child cycle counts now use the turn result usage request tally (the same number logged by :turn/ended), shared by list, end log, and parent stats. Focused scenario and unit spec passed; bb ci: 2000 specs, 966 feature examples, 0 failures (1 pre-existing pending). Gate passed on squash commit.

main-sha: isaac-agent 5019a204deeb901d4f29a99f33c946ddc319d8cf
