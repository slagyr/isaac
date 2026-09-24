---
# isaac-f3hq
title: 'The drive owns weather end to end: the sweep is scheduled, silence is weather, auth parks post attention'
status: in-progress
type: bug
priority: high
created_at: 2026-09-21T17:07:03Z
updated_at: 2026-09-21T17:32:02Z
blocking:
    - isaac-9azm
---

The drive already parks provider weather: `stamp-weather!` stamps the turn
marker with `:retry-at` and exponential backoff (30s doubling to 30m), and
`sweep-weather!` re-drives due markers (isaac-nqeq). But `sweep-weather!` has
**no production caller** — it is reachable only from
`spec/isaac/session/session_steps.clj`. Hail papered over that gap by
re-driving weather itself (`defer-delivery!`, flat retry-after, no
escalation) and by clearing the marker the bridge deliberately keeps
(isaac-3tvq/6zk5/5a4n, landed 2026-09-21 in isaac-hail `7e94025`).

**Ruling (Micah, 2026-09-21, isaac-9azm):** a turn that errors is weather,
and resuming it when the weather clears is the drive's responsibility. Hail
does not worry about it at all. This bean makes the drive's weather path
whole so isaac-9azm can delete hail's.

## Three pieces

1. **Schedule the sweep.** Register `sweep-weather!` on the shared scheduler
   (`isaac.scheduler.runtime`) the way `isaac.turn.worker` and
   `isaac.comm.delivery.worker` register their ticks: task id
   `:turn/sweep-weather`, interval 10s (the smallest backoff is 30s). Start
   it where the turn queue worker starts. Guard against double-driving: boot
   resume (`bridge/resume.clj`) already enqueues a due suspended marker on
   the turn queue and clears the marker, so the sweep must find nothing for
   it; and the sweep must skip a session that is in flight.
2. **Silence is weather.** An empty terminal response — after the one
   continuation nudge (isaac-k4mf) — is how an expired provider login looks
   on zanebot (claude OAuth expiry ⇒ `:empty-terminal-response` on every
   turn). Today it fails the turn with an error entry; hail retried it five
   times and dead-lettered. Under the ruling the drive parks it like a wall:
   reason `:silence`, same backoff, same sweep, nothing fabricated on the
   transcript. This replaces the k4mf failure entry in
   `features/session/error_handling.feature` and the empty-note failure in
   `features/llm/turn_exhaustion.feature`.
   **Assumption stated for Micah:** exceptions and generic API errors are
   *not* reclassified — they still end the turn `:error`, visible in the
   session, with the existing `:session/turn-failed` attention. Only
   silence joins wall/auth/stall. Widen later if the field says so.
3. **Auth parks post attention at once.** Hail posted a throttled attention
   notice on every auth deferral (isaac-5a4n); the drive only posts after
   `:turn :suspended-attention-ms` (6h). Only a human can re-login, so the
   drive posts on the first `:auth` park immediately and then at most hourly
   per provider while the park persists (the existing provider throttle in
   `isaac.attention`), and sets `:attention-posted` so the 6h notice does
   not double up.

## What this does NOT do

- No hail knowledge is removed here — that is the sibling bean (drive knows
  nothing about hail). No hail code changes — that is isaac-9azm.
- Do **not** repin isaac-hail in this bean; isaac-9azm bumps the hail pin.

## Scenarios

`isaac-agent/features/bridge/weather_suspend.feature` (three new `@wip`
scenarios: sweep registered; boot resume and sweep never double-drive;
silence parks and the sweep resumes it; auth park posts attention at once),
`features/session/error_handling.feature` (two k4mf scenarios recut to
park), `features/llm/turn_exhaustion.feature` (empty note after wrap-up
recut to park). New step: "the weather sweep is started" / "the scheduled
tasks include:" mirror the comm delivery worker's registration steps.

## Acceptance

```
cd isaac-agent
bb features features/bridge/weather_suspend.feature
bb features features/session/error_handling.feature
bb features features/llm/turn_exhaustion.feature
bb ci
```

- `sweep-weather!` is registered on the shared scheduler in the production
  boot path (a `grep -rn sweep-weather! src/` shows a caller outside
  `drive/weather.clj`).
- A due marker is driven exactly once across boot resume + sweep.
- Two empty responses park the turn (reason `:silence`) and the sweep
  completes it later; no error entry is written.
- The first `:auth` park posts attention immediately, throttled hourly per
  provider; the 6h threshold notice does not double-post.

feature-baseline: isaac-agent cabfdf29e81b87307a158b0fccd8056d0c03135d
feature-blob: isaac-agent features/bridge/weather_suspend.feature 63efea44b3c6ffecb0815774f8796bf2c1709e34 265,275,306,341
feature-blob: isaac-agent features/session/error_handling.feature 556af59adae06327cb50e80a3d1530f722b84375 76,99
feature-blob: isaac-agent features/llm/turn_exhaustion.feature 226278c152b1c46072b2b44568654e4ea0a8eebb 164



Dispatched: hail 5d575816 2026-09-21T17:15:30Z (band isaac-work)

## Checkpoint (2026-09-21, scrapper@2026-06-30-0021-icc9)

Turn was reassigned to another bean before this one closed. State of
`~/agents/isaac/work-1/isaac-agent-f3hq` (branch `bean/isaac-f3hq` @ 92cb392,
clean, 7 ahead / 2 behind `origin/bean/isaac-f3hq` — the local branch is a
rebase of the remote, so **push is a force-with-lease or a re-rebase**, not a
fast-forward).

Green: all three acceptance files pass on the branch.
- `features/bridge/weather_suspend.feature` — 16 examples, 0 failures
- `features/session/error_handling.feature` — 6 examples, 0 failures
- `features/llm/turn_exhaustion.feature` — 9 examples, 0 failures
- `grep -rn sweep-weather! src/` shows the production caller
  (`src/isaac/turn/worker.clj:127`), so that acceptance bullet is met.

**Not green: `bb ci` has 2 failures. Do not hand this off until they are
resolved.**

1. `features/session/tool_loop.feature:24` — "tool loop formats messages for
   OpenAI-compatible providers". Expected row 1 `role: tool`, got `assistant`.
   **This is a real regression from this branch**, not pre-existing: the same
   file run alone on a detached `origin/main` worktree passes (2 examples, 0
   failures), and fails alone on the bean branch (2 examples, 1 failure).
   Bisect so far: reverting the `:silence` arm of `weather-reason` in
   `src/isaac/drive/weather.clj` to `nil` does **not** fix it, so the silence
   reclassification is not the cause. Next step is the src-vs-spec split —
   `git checkout origin/main -- spec/` (keeping this branch's `src/`) and
   re-run that one feature; the prime suspect is
   `session_steps/with-feature-config!` + the `queue_steps` scheduler
   lifecycle, which now install and restore a process-wide config snapshot and
   register/deregister `[:scheduler]` in the nexus — global state that can
   leak across scenarios.
2. `features/config/schema_cli_options.feature:52` — passes when run alone
   (7 examples, 0 failures), fails only in the full `bb ci`. Cross-scenario
   pollution, same suspected cause as (1).

The temporary `weather.clj` edit made during the bisect was reverted; the tree
matches HEAD.

## Re-dispatch (planner, 2026-09-24)

Resume from isaac-agent `bean/isaac-f3hq` (835829f) and the checkpoint note above. isaac-600d (foundation: a snapshot read registers a nil config) is being redispatched in parallel and is a likely cause of the cross-scenario leak. If 600d has landed by the time you're bisecting, repin foundation to its main sha first and re-run `features/session/tool_loop.feature:24` and the full `bb ci` before any further bisecting. Do not hand off with `bb ci` red.
