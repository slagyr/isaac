---
# isaac-0uim
title: 'Wrap-up must checkpoint the worktree deterministically: the band names a checkpoint command the delivery worker runs before a continuation — the model ignores the commit nudge'
status: todo
type: bug
priority: critical
created_at: 2026-09-10T02:49:45Z
updated_at: 2026-09-10T02:49:45Z
---

Repo: isaac-hail (delivery worker wrap-up path; band config) + orchestration skills (band config on zanebot). Follow-up to isaac-xlx1 / isaac-ntt6 decision 5.

## Evidence (zanebot, isaac-mmod on isaac-work-2, 2026-09-09 19:48Z → 2026-09-10 01:40Z)
- Three `:turn/ended :ended-by :cycle-limit :exhaustion :wrapped-up` (19:48, 22:07, 01:40) → three `:hail/turn-continued` (continuation 1, 2, 3 — the band budget). In NONE of the wrap-up cycles did any git command run (no `tool/start` at all in the last minutes of each); the model answered the wrap-up nudge with a note only.
- The worktree `~/agents/isaac/work-2/isaac-agent-mmod` (`bean/isaac-mmod`): last commit 2026-09-09 17:01 (a CI repair), 37 dirty files after ~8 hours of continuation work. One more exhaustion at budget 3 would have dead-lettered the delivery with the work uncommitted. (Planner raised isaac-work `continuations` to 6 as a stopgap, 2026-09-10 03:0xZ.)
- The design (ntt6 decision 5) put the checkpoint inside the model's wrap-up cycle. With grok-4-6 it does not comply. A checkpoint that depends on the model is not a checkpoint.

## Required
1. Band config gains `:checkpoint <command>` (string; run via the shell in the bound session's cwd; env carries ISAAC_BEAN_ID, ISAAC_SESSION, ISAAC_CONTINUATION). The delivery worker runs it at wrap-up — after the model's wrap-up cycle, before re-queueing the continuation — and logs `:hail/checkpointed :session :continuation :exit :sha?`. Non-zero exit → `:hail/checkpoint-failed` at :warn with stderr, attention posted, continuation still queued (never lose the turn over a failed checkpoint).
2. The isaac/tono band templates set the checkpoint to the bean-branch WIP commit: e.g. `for d in $(git worktree list --porcelain | …); do (cd $d && git add -A && git commit -qm "wip: $ISAAC_BEAN_ID checkpoint (continuation $ISAAC_CONTINUATION)" --trailer Isaac-Bean:$ISAAC_BEAN_ID && git push -q -u origin HEAD) ; done` — the exact script lives in the orchestration repo (`isaac-beans/config/hail/…`) and zanebot's zane-isaac config; nothing is pushed to main (workers are on bean branches; tono-work moved to branches 2026-09-08).
3. The model's wrap-up nudge stays (note + handoff), but the commit is no longer its job.

## Scenario (@wip, planted isaac-hail 9753ad0, features/delivery.feature)
- wrap-up checkpoints the worktree deterministically before a continuation — existing steps + foundation's `the file … exists`. New steps: none.

## Acceptance
- planted scenario green with @wip removed; `bb features && bb spec` green in isaac-hail
- Band config on zanebot (isaac-work, tono-work, orchestration-work) carries `checkpoint`; after deploy, the next wrap-up on any worker session shows `:hail/checkpointed` and a new commit on the bean branch.
