---
# isaac-ohsy
title: 'Turn-request queue core: park on :hold, durable, wake by tick + token, turns list/drop'
status: in-progress
type: feature
priority: high
created_at: 2026-08-25T18:39:48Z
updated_at: 2026-08-25T18:58:52Z
---

Likely repo: **isaac-agent**. Depends on isaac-opp6 (turnstile protocol) and
isaac-bbov (finalization + release tokens), both landed. Defined in the
2026-08-24 architecture session (see isaac-bbov, isaac-tdgt) as "next
session"; this bean is that session's output (2026-08-25, Micah + plan).

## Problem

opp6 ships `(admit? ctx) -> :pass | :hold | {:refuse reason}` plus release
tokens and the built-in `:tide` turnstile, but a `:hold` today is reported as a
refusal and dropped. Hail survives on its own deferral path; foreman's `:turn`
action and the CLI have nothing. "run at 22:00" (a request holding at tide) and
worksite-pool selection both need a real waiting room.

## Design

1. **submit** takes charge + turnstile stack + address spec
   `{:session | :crew | :tags, :reach}`.
2. **:hold parks the request durably** (survives restart — relates to
   isaac-wq8m). `:refuse` stays loud and unchanged.
3. **Release-token firing is the wake signal**: on wake, re-run `admit?` on
   held requests in submit order; first stack that fully passes runs.
4. **Candidate resolution in core**: from an address spec, pick one candidate
   whose whole stack passes — the same algorithm worksite pools (W2) and hail
   selection will use. Hail contracts toward naming + records over this queue.
5. CLI `--turnstile tide:22:00-06:00` becomes a real delayed prompt (the
   "wording chosen to stay true once holds become parks" in opp6 scenario 1
   now flips to a park).

Invariants (session single-writer, provider walls) remain core, never
turnstiles. No hail↔foreman arrows; both plug into this queue only.

## Decisions (2026-08-25, Micah + plan)

1. **Split**: this bean is queue CORE (submit → park on :hold → durable
   record → wake → run, plus `isaac turns list|drop`). Candidate selection by
   address spec is **isaac-ohsy-B** (see the bean that blocks on this one).
2. **Wake = ticks + tokens.** A `turn-queue` worker ticks on the scheduler
   (clock path; tide can only wake by clock; fallback that keeps holds alive).
   A release token — minted on :pass, invoked by finalization when that turn
   ends on ANY outcome — nudges an immediate wake ("turn finished behind X,
   re-admit whoever waits at X").
3. **CLI on hold parks and returns**: prints `held: <id> (<turnstile> <reason>)`,
   exit 0; reply lands in the session transcript when the turn runs.
   `--wait` (optional, not scenarioed) blocks until completion. This flips
   opp6's tide scenario (exit 1 → exit 0) — it is re-@wip'd in
   features/turn/turnstiles.feature under this bean.
4. **`isaac turns list | drop <id>`** — humans see and evict the waiting room
   (parallels worksites list/lock/unlock).
5. **Submit order.** On each wake walk held requests in submit order; run every
   one whose whole stack passes. Session single-writer still serializes holds
   on one session.

Held record lives under the isaac root (exact path is implementation detail —
steps assert via `turns list` only). Hail's `defer-delivery!` +
`the delivery worker ticks at` is the pattern to mirror, not reuse.

## Scenarios (committed @wip at slagyr/isaac-agent b8d71e6)

features/turn/turn_queue.feature — 5 scenarios:

- :22 tide hold parks; tick outside the window does nothing; tick inside runs
  it; `turns list` empties.
- :47 held turn survives a restart (runtime re-booted via
  `the comm delivery system is started`) and runs on the next tick.
- :59 closed test turnstile parks; opening it wakes via the token path — no
  tick.
- :76 admits-1 turnstile: in-flight turn holds two more; ending it fires the
  token → jetty runs → quay runs, submit order, no tick.
- :102 `turns drop <id>` evicts; later tick does not run it; the queued echo
  survives to a bare prompt.

features/turn/turnstiles.feature:17 — tide hold re-@wip'd with park semantics.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / the following sessions exist / the current time is / the following model responses are queued (incl. `wait`) / isaac is run with / stdout contains / stdout matches (+ capture `:held-id`, `#held-id` ref) / stdout lines contain in order / stdout does not contain / exit code / session has transcript matching / the user sends … on session / the turn ends on session / the comm delivery system is started | reuse |
| **the turn queue ticks at {iso}** | **NEW — drives the queue worker at a pinned clock (mirror of `the delivery worker ticks at`)** |
| **a turnstile {string} is registered that admits {int} at a time** | **NEW — registers a scripted test-double turnstile BY NAME in `isaac.turnstile` (same registry seam worksite uses)** |
| **turnstile {string} is closed / is opened** | **NEW — opened fires the double's release path so the token wake is exercised** |
| **the user sends {string} on session {string} with turnstiles {string}** | **NEW — comm-path submit carrying a turnstile stack (charge `:turnstiles`)** |

## Acceptance

In slagyr/isaac-agent — remove `@wip` from features/turn/turn_queue.feature
and from turnstiles.feature:17, then:

    bb features features/turn/turn_queue.feature
    bb features features/turn/turnstiles.feature
    bb spec spec/isaac/turnstile_spec.clj spec/isaac/drive spec/isaac/bridge
    bb features features/bridge/cli-prompt.feature features/session

Full `bb features` stays 0 failures under the 180s budget.

## Out of scope

- Address-spec candidate selection and hail router cutover (isaac-ohsy-B).
- `--wait` blocking mode (nice-to-have; add a scenario if built).
- Foreman's :turn action (consumes this queue; foreman repo).
