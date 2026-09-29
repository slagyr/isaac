---
# isaac-2lc4
title: 'isaac-agent: queued turns never run in production — the turn queue tick does not fire'
status: completed
type: bug
priority: critical
created_at: 2026-09-29T18:42:29Z
updated_at: 2026-09-29T19:25:32Z
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

## Root cause (2026-09-29)

Reproduced locally (no zanebot access): booting the real `isaac.runner`
(scheduler + components) with the real `:isaac.agent` module wired in — the
same code shas deployed at incident time, agent `6aa86a3` / foundation
`95e44ce`, diff clean against current `main` on both — shows the turn-queue
component starting, `:turn.queue/tick` registering on the shared scheduler,
and the interval firing correctly on its own. So the shared
runner/scheduler/component wiring is sound; the gap was that no feature ever
exercised it (every existing scenario drove the queue via the manual `the
turn queue ticks at` step, calling `isaac.turn.worker/tick!` directly and
bypassing module-index → component-factory → component/start →
`turn/worker/start!` → `scheduler/schedule!` entirely).

While building that missing coverage, found and fixed a real bug in
`isaac.turn.worker/tick!`'s coalescing state machine
(`tick-state*`: `:idle`/`:running`/`:pending`), which explains the "started,
scheduled every 10s, then total silence forever" shape of the incident
without requiring any zanebot-specific theory:

- A wake that lands while a tick is already running is coalesced —
  `request-tick!` CASes `:running` → `:pending` instead of dropping it, and
  the running tick's `finish-tick!` picks that up as one more owed pass
  (returns `:run`, the loop `recur`s).
- But if the *currently running* pass throws (e.g. a transient `fs`/EDN
  write failure updating a turn's final state, or any other exception from
  `queue/list-held` / `queue/claim!` / `process-record!`'s uncaught tail),
  the old code called `finish-tick!` (correctly advancing state) and then
  **immediately re-threw**, skipping the `(recur)` that would have taken the
  owed pass and returned state to `:idle`.
- When that owed pass existed (`finish-tick!` returned `:run`, i.e.
  `:pending` → `:running`), the early re-throw left `tick-state*` stuck at
  `:running` forever. Every later wake — the 10s interval, and the
  resource-pool release wake-hook — then CASes `:running` → `:pending` and
  returns without doing anything (a "no-op" is a legitimate outcome of that
  state machine, not a bug in itself). No exception, no
  `:turn.queue/woke`, nothing — matching "component started, tick
  scheduled, then total silence" exactly. Only a process restart (a fresh
  `(defonce tick-state* (atom :idle))`) clears it, matching why a rollback
  "fixed" it.

Fix: `isaac.turn.worker/tick!` now loops on the pass's own failure — it
always takes every pass `finish-tick!` still owes (keeps `recur`ring while
`next-state` is `:run`, accumulating the last failure) and only re-throws
once `finish-tick!` has actually returned the state to `:idle`. `tick-state*`
can no longer get stranded off `:idle` by a pass that throws while a wake is
coalesced onto it.

New coverage:
- `features/turn/turn_store.feature:162` (`@wip` removed, no `@slow`): boots
  the real runner via `the Isaac runner is started` and a new isaac-agent
  spec step, `within {n} seconds session {key} has transcript matching:`
  (polls the existing transcript-matching logic; reused, not duplicated),
  and asserts a `prompt --queue`d turn actually runs on the server's own
  10s tick with no manual tick step.
- `spec/isaac/agent/runtime_steps.clj` (new): advises foundation's
  `the Isaac runner is started` step (`alter-var-root`, same pattern already
  used in `isaac.turn.queue-steps` for `fcli/parse-argv`) so it always
  includes the real `:isaac.agent` module — read from
  `resources/isaac-manifest.edn` — in the runner's `:module-index`. Without
  this the foundation step's hardcoded index carries only `:isaac.foundation`
  and none of the agent's `:isaac/component` contributions
  (`:agent-lifecycle`, `:comm-delivery`, `:turn-queue`) ever get
  instantiated — a test-harness gap, not the production bug, but it's why
  the production tick path had zero feature coverage. Foundation itself did
  not need to change.
- `spec/isaac/turn/worker_spec.clj`: "does not wedge the tick loop forever
  when a coalesced wake arrives during a failing pass" — reproduces the
  deadlock deterministically (a nested `tick!` call from inside a redefed
  `queue/list-held` simulates the coalesced wake, then that pass throws),
  fails red against the pre-fix code, green after.

No isaac-foundation change was needed.

## Landed on main (2026-09-29)

main-sha: isaac-agent 505a60edfab0ab91115e143f73a922d0ff54eb37
