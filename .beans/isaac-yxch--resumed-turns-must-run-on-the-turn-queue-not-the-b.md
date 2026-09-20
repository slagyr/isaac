---
# isaac-yxch
title: Resumed turns must run on the turn queue, not the boot thread
status: todo
type: bug
priority: high
tags:
    - agent
    - ops
created_at: 2026-09-20T18:41:53Z
updated_at: 2026-09-20T20:17:59Z
---

Repo: **isaac-agent** (with isaac-foundation if the component boundary moves). Found 2026-09-20 on zanebot.

## What happened

`isaac.agent.component/AgentLifecycle.start` calls `resume/resume-interrupted-turns!`, which **executes each resumed turn synchronously on the boot thread**. One resumed heartbeat turn ran a pathological `fs__glob` (isaac-01vv) and the entire server sat in `component/start-all!` for ten minutes: no HTTP listener, no hail delivery, no cron, nothing. Three restarts in a row stalled the same way, because every boot resumed the same marker and re-ran the same glob.

Thread dump at the time: `main` parked in `isaac.llm.tool-loop/execute-tool-batch` → `run! deref workers`, with a send-off pool thread burning CPU inside the glob. Boot had logged `server/started` and nothing after it.

## Why this is the wrong shape

Resume is recovery work, not boot work. A turn can take minutes legitimately — a long tool, a slow provider, a cycle budget of 250. Boot must not be hostage to it. Worse, the failure is silent from outside: the process is alive, the port is closed, and the log simply stops.

## Change

- Resumed turns go through the **normal turn queue** (the same path a hail or comm turn takes), not the boot thread. `start` scans markers, enqueues, and returns.
- `component/start-all!` finishes and the HTTP listener binds regardless of how long any resumed turn takes.
- Resume logs stay: `resume/scan-complete` with counts at scan time, then per-turn outcomes as they finish.
- A marker whose turn cannot be enqueued (store unavailable, session gone) is dropped with a warning, as today.

## Scenarios (worker writes; isaac-agent `features/`)

- the runner finishes starting every component while a resumed turn is still running
- a resumed turn that takes longer than boot still completes, and its outcome is logged
- resume with no markers leaves boot timing unchanged
- a marker outside the resume window is still dropped, not enqueued

## Acceptance

`cd isaac-agent && bb ci`, plus: with a deliberately slow resumed turn, `http/listening` appears in the log **before** that turn ends.

## Related

isaac-01vv (the glob that triggered it). Operationally, the stale markers on zanebot were moved aside by hand as `turn.edn.stalled-20260920`; a planner could not clear them because `isaac turns list` does not show resume markers — worth considering a `isaac turns drop --marker` or similar so this is recoverable without touching files.

Dispatched: hail f780219f 2026-09-20T18:44Z (band isaac-work)

Dispatched: hail a86d8b36 2026-09-20T20:17:51Z (band isaac-work)
