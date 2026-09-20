---
# isaac-yxch
title: Resumed turns must run on the turn queue, not the boot thread
status: completed
type: bug
priority: high
tags:
    - agent
    - ops
created_at: 2026-09-20T18:41:53Z
updated_at: 2026-09-20T20:53:38Z
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

## Implementation (2026-09-20, scrapper@isaac-work-2)

Repo: **isaac-agent**, branch `bean/isaac-yxch`, commit `5404302`. No
isaac-foundation change was needed — the component boundary did not move.

- `isaac.bridge.resume`: `dispatch-comm-resume!` (which called
  `isaac.drive.turn/run-turn!` inline) is replaced by `enqueue-resume-turn!`,
  which appends the interruption note through the session policy and parks a
  record on the normal turn queue (`isaac.turn.queue/enqueue!`, bound to the
  resume root). The scan clears the marker and returns; the queue worker drives
  the turn and logs `:turn.queue/woke` when it finishes. Both inline paths (the
  `:comm`/`:cron`/`:cli` branch and the weather `retry-at`-passed branch) now
  enqueue; `clear-marker!` is extracted and used by every branch.
- The note is persisted at scan time because the queue worker drives a
  `:from-queue? true` charge and never re-appends the input — same contract as a
  parked CLI turn, whose user message is persisted at submit.
- Enqueue failure is caught: `:warn :resume/enqueue-failed`, the marker is
  dropped, and it counts as `:dropped` in `:resume/scan-complete` (counts and
  the scan-complete log are unchanged otherwise).
- `isaac.bridge.core/marker-source` honours `(:source origin)` so a resumed
  comm turn that is interrupted *again* still writes a `:comm` marker and the
  staleness window keeps applying (the queue charge carries no comm object).

Tests: `features/session/resume_queue.feature` (4 scenarios: enqueued-not-run,
queued turn completes + outcome logged, no markers → nothing enqueued, stale
marker dropped not enqueued); `features/session/resume_repair.feature` scenarios
that asserted inline completion now tick the queue; 3 new examples in
`spec/isaac/bridge/resume_spec.clj` (enqueue instead of `run-turn!`, weather
retry enqueue, enqueue failure → warn + dropped).

`cd isaac-agent && bb ci` green: 1670 spec examples / 840 feature examples,
0 failures.

On the acceptance's live check (`http/listening` before a slow resumed turn
ends): the scan no longer runs *any* turn, so no resumed turn can be in flight
while components start. That is asserted two ways — the unit spec redefines
`isaac.drive.turn/run-turn!` to record calls and asserts none, and the feature
asserts the transcript carries no reply until the queue ticks.

Operational bonus for the Related note: resumed turns now appear in
`isaac turns list` as held records and can be evicted with `isaac turns drop
<id>`, so stalled resume work is recoverable without touching marker files.


## Verified (2026-09-20, perceptor@isaac-verify-2)

Verified against the bean acceptance in **isaac-agent** (branch bean/isaac-yxch @ 5404302, based on origin/main@e948ce3).

- Diff scope vs origin/main: 5 files only - features/session/resume_queue.feature (new, 4 scenarios, no @wip), features/session/resume_repair.feature (+2 queue-tick steps), spec/isaac/bridge/resume_spec.clj (+3 examples), src/isaac/bridge/core.clj, src/isaac/bridge/resume.clj. No deps.edn/bb.edn pin change; no isaac-foundation change (component boundary did not move, as the bean allowed).
- bb ci on the branch: **1670 spec examples / 0 failures / 3454 assertions**, **840 feature examples / 0 failures / 2000 assertions / 1 pending**. The one pending is pre-existing and untouched (features/session/compaction_mid_turn.feature:12 "rubberband mid-turn still carries the originating hail").
- Targeted: bb features features/session/resume_queue.feature -> 4 examples, 0 failures, 12 assertions.
- Boot-thread claim confirmed structurally: grep for run-turn! / drive.turn in src/isaac/bridge/resume.clj -> none. resume.clj now only enqueues (isaac.turn.queue/enqueue!) and clears markers; src/isaac/agent/component.clj:38 still calls the scan, which returns without driving a turn. The acceptance's "http/listening before the slow resumed turn ends" therefore cannot be violated - no resumed turn can be in flight during start-all!.
- Scan-time behaviour preserved: staleness drop happens before enqueue (scenario 4), counts unchanged in :resume/scan-complete, enqueue failure -> :warn :resume/enqueue-failed + counted :dropped (spec).
- Pin rule: isaac-foundation 294321de is an ancestor of isaac-foundation origin/main.
- Squash commit tree equals the branch tip tree (verified) - the green gate ran on the landed tree.

## Landed on main (2026-09-20)

main-sha: isaac-agent fb6d06199ddd8e0a45a127095bf769fdb50aa2bc
