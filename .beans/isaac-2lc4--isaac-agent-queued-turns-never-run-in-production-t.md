---
# isaac-2lc4
title: 'isaac-agent: queued turns never run in production — the turn queue tick does not fire'
status: in-progress
type: bug
priority: critical
created_at: 2026-09-29T18:42:29Z
updated_at: 2026-09-29T18:56:50Z
---

Found 2026-09-29 smoking the zanebot deploy (agent 6aa86a3; rolled back). A hail sent over HTTP was accepted as turn 7a04b76a (state :queued, frequencies {:session-tags #{:smoke} :create :never}; a :smoke session exists) at 18:37:05 and stayed :queued for 5+ minutes. The `turn-queue` component started at 18:33:09, `isaac.turn.worker/start!` schedules `:turn.queue/tick` every 10 s on the runner's shared scheduler, yet the log shows no `:turn.queue/woke` after boot. Features drive the queue with the `the turn queue ticks at` step, so the production tick path is untested.

Investigate: is the interval task actually firing (scheduler started, task registered after nexus :scheduler is set)? Does `tick!` → `queue/list-held` include records in state `:queued` (isaac-70cr added that state) or only held/waiting ones? A feature must boot the real runner and see a queued turn run with no manual tick.


## Acceptance (2026-09-29, Micah approved the new step)

- [ ] `bb features features/turn/turn_store.feature:162` (isaac-agent, `@wip` on main at 98ebed0) passes with `@wip` removed — no manual `the turn queue ticks at`. Must NOT be tagged `@slow` (CI skips @slow; that is how the tick went untested).
- [ ] New step in isaac-agent spec: `within {n} seconds session {key} has transcript matching:` — polls the transcript until it matches or times out.
- [ ] `the Isaac runner is started` (foundation step) must boot Agent's `turn-queue` component; extend the harness if the runner's module index lacks it.
- [ ] Root-cause and fix why the `:turn.queue/tick` interval task never fired on zanebot (see the bean body). Record the cause in the bean.
- [ ] Whole Agent suite green; version bump; `bb verify`.

feature-baseline: isaac-agent 98ebed010249137aab45a9a632082d81d3aa9d05
feature-blob: isaac-agent features/turn/turn_store.feature 30444ad474eb5606693481f2455b5981c9d85f3d 162
