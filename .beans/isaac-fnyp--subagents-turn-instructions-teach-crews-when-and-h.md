---
# isaac-fnyp
title: 'Subagents: turn instructions teach crews when and how to delegate'
status: draft
type: task
priority: normal
tags:
    - agent
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-01T18:45:06Z
parent: isaac-4qxr
blocked_by:
    - isaac-p5d3
    - isaac-urog
---

Repo: **isaac-agent** (turn instructions). Follows isaac-p5d3 and the list/cancel bean.

Once crews can spawn subagents, tell them how to use them: hand off file sweeps and research that would flood the context, keep only the conclusion, stay available to the person while children run, pick a cheaper model for simple work and a stronger one for hard work, and don't spawn for something a few tool calls would answer.

## Acceptance

- The turn instructions for crews with `agent__spawn` include the delegation guidance; crews without the tool don't get it.
- Measure: rerun a sweep-heavy task (e.g. the tono-jlzi pattern) and compare parent context growth and compactions before and after.
