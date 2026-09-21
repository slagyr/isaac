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
