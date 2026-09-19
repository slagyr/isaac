---
# isaac-jp4v
title: 'Bean Gate: planner overlay — baseline step in AGENTS.md Planning + dual-run'
status: draft
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-19T20:43:16Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
---

Child 2 of isaac-rmq6. Draft until isaac-cy85 lands (the command shape is settled there).

Planner overlay: AGENTS.md `## Planning` gains the baseline step — push `@wip` scenarios to the module's **main** (module CI excludes `@wip`, so main stays green), then `bb bean-gate baseline <id> <repo>:<path>[:<line>…]`, commit the bean. Planner-authorized feature edits after a baseline: edit the feature on main, re-run `baseline` (appends new lines). Plus the one-liner for the `isaac-plan` band / hail-bean-plan overlay. Dual-run starts here: workers still hail verify; the verifier also runs `bb bean-gate verify <id>` and records the result.
