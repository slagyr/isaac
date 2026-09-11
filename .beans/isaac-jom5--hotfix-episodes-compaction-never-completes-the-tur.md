---
# isaac-jom5
title: 'HOTFIX: episodes compaction never completes — the turn keeps measuring the closed episode, logs no-progress, and re-compacts every turn, spawning open successors'
status: completed
type: feature
priority: critical
created_at: 2026-09-09T21:22:23Z
updated_at: 2026-09-10T00:05:13Z
---

Repo: isaac-agent (`session/compaction.clj` splice! / compact-close! path, `drive/turn.clj` compaction retry + successor-session-key). Agent 0.1.52 on zanebot. Interim hotfix ahead of isaac-mmod (which moves this whole path behind the session policy); keep the change minimal and scoped to the episodes branch.

## Evidence (marvin ACP episode 2026-09-09-1731-gv5a, thread acp-4cb3db22…, 2026-09-09 21:10–21:21Z, cli.log)
- session record: last-input-tokens 420,856 (provider 377,405; drift 2.40), 442 entries, `compaction-count 0`, 826 KB.
- 21:10:13 compaction-started → 21:14:32 `:episodes/closed gv5a` (compact-close! opened successor 2026-09-09-2114-junk, parent gv5a) → 21:14:39 `:session/compaction-stopped :reason :no-progress :total-tokens 168373 :attempt 1`.
- 21:15:33 compaction-started again (analysis: 442 entries, compact-count 135, slinky, chunked ×4 within a 32K budget) → 21:17 successor 2026-09-09-2117-7dag (parent junk) → 21:18:01 no-progress (168479). 21:18:14 started again → 21:21:00 no-progress (168725).
- Net: three rounds, two OPEN successor episodes on the same thread, the original still full; every turn ships ~377K tokens to grok.

## Cause (read of the code path)
On an episodes crew, `splice!` calls `lifecycle/compact-close!`, which closes the episode and opens a successor seeded with the summary. The drive then re-measures the ORIGINAL session key (a closed episode whose transcript is immutable) → no reduction → `compaction-stopped :no-progress`; the mid-turn `successor-session-key` switch happens only after a successful compaction, so it never runs. Next turn repeats.

## Required (minimal)
1. After compact-close! produces a successor, the drive continues the turn on the successor's session key and judges progress on THAT transcript (the summary + live message), i.e. completed, not no-progress.
2. A thread must have at most one open episode: compact-close! must not open a new successor when one already exists for the thread (reuse it).
3. Log `:session/compaction-completed` with the successor id.

## Scenario (@wip, planted isaac-agent 955b882, features/episodes/live.feature:610)
- compaction on an episodes session hands the turn to the successor and measures progress there — existing steps only (+ the negative log step from isaac-o2fh if it landed; else it is NEW: `Then the log does not have entries matching:`).

## Acceptance
- `bb features features/episodes/live.feature:610` green with @wip removed; `bb features features/episodes/ features/session/compaction_*.feature && bb spec` green
- Train + field: agent bump, deploy; on zanebot the two dangling successors on thread acp-4cb3db22… (2114-junk, 2117-7dag) are closed by `isaac episodes close` after deploy; a new marvin ACP session compacts once and continues.


## Handoff

branch: bean/isaac-jom5 @ 9b0030d4c8a0c9b9690ae5da56c5cf23cf1d2509 (base origin/main@7c05aeffff36cbe50027eb2c3c07ef0ff03a1fb9)

After compact-close! the drive continues on the successor session key and
logs :session/compaction-completed with that id. no-progress only when the
same key did not shrink. compact-close! reuses an already-open episode on
the thread. Scenario live.feature:610 green, @wip removed. episodes/ +
compaction_* (except pre-existing compaction_memory_flush flake on main)
green. session_steps now pass :crew/:config so episodes dispatch resolves
the thread.

## Landed on main (2026-09-09)

main-sha: isaac-agent 644bd4f0be04278c9e0a7ceb11c839d2f0bb7231



## Deployed (2026-09-09 ~21:5xZ) — agent 0.1.53 (f7432c2)
Pinned, upgraded, restarted; `config validate` OK; marvin CLI pong OK. Before the deploy the loop had kept running on Micah's thread: SIX open successors (2114-junk … 2130-nf7j) — closed by the planner after deploy (see thread cleanup note). Field proof = Micah's next marvin ACP session compacting once and continuing.
