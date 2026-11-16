---
# isaac-cpiz
title: 'Subagents: a suspended (weather) child is reported to the parent as failed'
status: todo
type: bug
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:40Z
updated_at: 2026-11-16T14:44:40Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (subagent result observer, `tool/subagent.clj`).

## Observed (zanebot, 2026-11-15, child "Restful Newt")

The child's provider returned a 400 and was walled for 30 minutes. The child's turn ended `:ended-by :provider-unavailable` and was **suspended** with `:retry-at` 03:49 — it will run again. But the observer logged `:subagent/ended :outcome "failed"` and delivered a failed result to the parent at once. If the retry succeeds, the parent gets a second, contradictory result after it already gave up.

## Fix

A suspended (weather) child is not finished. The parent is told the child is waiting on weather (or nothing yet), and gets exactly one final result when the child really ends. `subagent__list` shows the state as suspended/waiting, not failed.

## Acceptance

Scenario in `isaac-agent/features/tool/subagent_control.feature` (scenario-level `@wip`; remove it when it passes):

- `bb features features/tool/subagent_control.feature:130` — a child walled by a 429 is listed `suspended`; after the resume sweep it completes, is listed `done`, logs exactly one `:subagent/ended` (outcome ok), and the parent gets one result

`subagent__list` gains the state `suspended`. In the test the walled child is listed `done` today (zanebot showed `failed`): the observer treats the suspension's turn end as final.

feature-baseline: isaac-agent af7101ebd7321fe96dd4d9c0c90039ffb5c0ff68
feature-blob: isaac-agent features/tool/subagent_control.feature a2b02bda946ac4b187d3729ae71da63f39e50012 130
