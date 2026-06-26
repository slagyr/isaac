---
# isaac-evws
title: Hail-driven worker bootstrap/docs gap surfaced by orchestration smoke test
status: todo
type: task
priority: low
created_at: 2026-06-26T16:39:58Z
updated_at: 2026-06-26T16:39:58Z
---

## Summary
Hail-driven worker bootstrap for process/bean work is underspecified when the promised checkout/session path does not exist locally and referenced skills are not discoverable in-session.

## Problem
During the orc1 process-smoke run, the worker hail implied a ready checkout/session context, but the stated `isaac-1` / `quarters` path was not present. The worker had to manually discover the actual repo checkout before claiming the bean. Also, `list_skills` returned no discovered skills even though the workflow references hail/bean-work skills.

## Desired outcome
A hail-driven worker should be able to start from the hail alone and know:
- the authoritative repo checkout path or how to derive it
- whether the referenced skills are expected to be available
- the documented fallback path when skill discovery fails
- the minimal expected procedure for no-op process-test beans

## Acceptance ideas
- Worker docs or skill specify authoritative repo/session discovery.
- Worker docs include a fallback when `list_skills` returns empty.
- No-op orchestration/process-test beans have an explicit worker checklist.
