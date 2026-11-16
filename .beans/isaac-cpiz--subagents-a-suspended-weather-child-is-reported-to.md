---
# isaac-cpiz
title: 'Subagents: a suspended (weather) child is reported to the parent as failed'
status: draft
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

Draft — scenario to come: a child whose provider walls is listed as suspended, the parent gets no failed result, and when the retry succeeds the parent gets one ok result.
