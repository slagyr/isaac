---
# isaac-c621
title: Turn observers are lost when a turn resumes after a restart, and a shutdown suspension looks like an end (Foreman instances strand)
status: draft
type: bug
priority: high
tags:
    - agent
    - foreman
created_at: 2026-11-16T18:01:24Z
updated_at: 2026-11-16T18:01:24Z
---

Repos: **isaac-agent** (boot resume / turn observers) and **isaac-foreman** (turn observer). Seen 2026-11-16 on zanebot.

## Observed

Four bean-work instances on `isaac-bean-work` lost track of their worker turns across server restarts (16:31, 16:51, 17:24, and one ~15:52 — other deploys that day):

- **cpiz, jjca, by8s:** each worker turn was interrupted by a restart, resumed by boot resume, then finished with a "Task complete … landed" reply. No `:foreman/reply-event` was logged and the instance stayed `working` for up to 2 h until the planner sent `landed` by hand. The resumed turn ran without the Foreman turn observer.
- **hu1n:** the restart at 17:24:48 suspended its turn (`:ended-by :suspended`); the Foreman observer treated that as the turn ending and moved the instance to `stalled` ("alert skipped: no target session"). The resumed turn finished at 17:29 with a landed reply that nobody parsed.

Turn records already persist `:observers` (e.g. `[[:subagent …]]`), so the data survives; the resume path doesn't re-attach them, and a suspension is reported to observers as an end.

## Fix

- A turn resumed after a restart runs with the same observers it was submitted with (Foreman's work/verify observer, `:subagent`, …).
- A suspension (shutdown or weather) is not an end: observers get no `on-turn-ended` until the turn really finishes.

Relates to isaac-cpiz (the same suspension-vs-end confusion in the subagent observer).

## Acceptance

Draft — scenarios to come: a Foreman work turn interrupted by a restart and resumed still moves its instance on its reply; a turn suspended at shutdown leaves the instance `working`.
