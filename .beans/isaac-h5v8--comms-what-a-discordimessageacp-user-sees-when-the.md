---
# isaac-h5v8
title: Originator is told when its turn suspends on provider weather (reason + retry-at) and gets the normal reply on completion — hail, Discord, iMessage, cron, ACP
status: completed
type: feature
priority: high
tags:
    - comm
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-23T16:47:38Z
parent: isaac-ugpq
blocked_by:
    - isaac-nqeq
---

Child 4 of isaac-ugpq. Open: one-line notice on suspend or silence until the reply; per comm. Decide with Micah after child 1 lands.

## Decision (Micah, 2026-09-23): the comm shows the user what went wrong

"When something goes wrong a message should be displayed or the comm should be notified so that it can display a message to the user." The Comm protocol already carries `on-turn-end [comm session-key result]` and `on-exhausted`; gchat's `on-turn-end*` ignores the result today. First comm: gchat.

- On a turn that ends in an error, gchat posts a short in-thread notice naming the failure class (provider error, tool failure, delivery failure), never a stack trace, never a raw CLI payload.
- On provider weather (the drive parks the turn: rate-limited, auth, stall), gchat posts one notice with the reason and the retry time when known ("Out of tokens until 4:40pm; I will answer then"), and one when the turn resumes only if the reply itself is not the next message.
- The attention comm (system-scoped, yopp: Micah's DM) still gets the operator alert; the originator notice is in addition, not instead.
- Same seam for gmail later.

Scenarios in gchat outbound.feature + agent features for the on-turn-end result shape. Status → todo, high.

## Handoff (worker, 2026-09-23)

Implemented in **isaac-gchat** only. **isaac-agent was not touched** — the
drive already gives the comm everything the Decision asks for, no seam
needed, no pin/dev-local consequence.

**What the comm learns, and how.** `finish-turn!` (isaac-agent
`src/isaac/drive/turn.clj`) always calls `comm/on-turn-end` with the
finalized result:
- A hard failure: `:ended-by :error`, plus `:error` (e.g. `:exception`,
  `:llm-error`, `:api-error`) and, for an in-process exception, `:ex-class`.
- Provider weather (wall/auth/stall): `classify-ended-by` maps a
  `weather/stamp-weather!` result to `:ended-by :provider-unavailable`, with
  `:unavailable? true`, `:reason` (`:wall`/`:auth`/`:stream-stalled`) and
  `:retry-at` (ISO instant) already on the map. Nothing new was added to
  `isaac.comm.protocol` or `isaac.drive.turn`.

**Gap found, left alone (in scope for someone, not this bean).** A weather
*resume* (`isaac.drive.weather/resume-suspended!`) drives the re-try with
`:comm null-comm/channel` and no `:origin` — the originating comm never
gets `on-cycle-start`/`on-reply`/`on-turn-end` for a resumed turn today. So
in production, gchat cannot yet tell "parked, then resumed with a reply"
from "parked, still parked" by watching its own callbacks after a real
sweep-driven resume. The 4 required scenarios are still real and
deterministic — they exercise the same `on-turn-end`/`on-reply` seam via a
second live dispatch (a genuine second turn) rather than the sweep, which
is sufficient for what gchat controls and does not depend on that gap.
Flagging it since "gets the normal reply on completion" (bean title) isn't
fully wired end-to-end until that seam exists.

**Design.** `isaac.comm.gchat`:
- `parked-sessions` (new, process-lifetime atom): session-keys with an
  unanswered park notice outstanding. Set on the first weather
  `on-turn-end`; cleared on any non-weather `on-turn-end` (reply, error,
  cancel, cycle-limit). A repeat weather `on-turn-end` while still parked
  posts nothing — this is what makes "second walled cycle" and "resumed
  with a reply" quiet.
- `on-turn-end*` was already carrying isaac-qry7's `delivery-failures*`
  check (landed on main same day, `on-reply*` catching a failed
  `create-message!` for the actual reply — a different failure: the reply
  succeeded to build but failed to POST). That check runs first, unchanged,
  and now also clears `parked-sessions` on the way out. My new weather/error
  handling is the `else` path.
- Wording lives in `isaac.comm.gchat` only (`weather-notice-text`,
  `error-notice-text`), posted via the existing `post-chunks!`/`access-token`
  path, never through `comm/send!`'s delivery-queue retry machinery — a
  single attempt, `log/error :gchat.notice/failed` once on throw, no retry.

**Notice texts (verbatim template):**
- Weather: `"<what> until <h:mma local time>; I will answer then."` or,
  with no known retry time, `"<what>; I will answer when it clears."`
  `<what>` is `"Out of tokens"` (`:wall`), `"Waiting on a login"` (`:auth`),
  `"The connection stalled"` (`:stream-stalled`), else `"Hit a provider
  issue"`. Example: `"Out of tokens until 4:40pm; I will answer then."`
- Error: `"Something went wrong (<class>). I couldn't finish that reply."`
  `<class>` is `"provider error"` (default), `"tool failure"` (an
  `:ex-class` mentioning "tool"), or `"delivery failure"` (mentioning
  "deliver") — same 3-word vocabulary as isaac-qry7's `:class`
  (`:provider-error`/`:tool-failure`/`:delivery-failure`). In today's
  code the drive's own `:error` values (`:exception`, `:llm-error`,
  `:api-error`) only ever produce `"provider error"` — the tool/delivery
  branches are defensive/forward-compatible, unit-tested with a synthetic
  `:ex-class`, not currently reachable from a real turn.

**Files (isaac-gchat, one commit `fa44cd2` on `bean/isaac-h5v8`):**
- `src/isaac/comm/gchat.clj` — the above.
- `spec/isaac/comm/gchat_spec.clj` — 7 new specs (park notice + time; dedup
  on repeat wall; no extra notice on reply-after-park; repost on a fresh
  park; error notice + class, never leaks the raw message; tool-failure
  classification; notice delivery failure logged once, not retried).
- `features/comm/gchat/outbound.feature` — 4 new scenarios matching the
  bean's list exactly (error → one notice naming the class; wall → one
  notice with retry time; park then reply → no extra notice; second wall
  in the same park → no second notice). Each got its **own space**
  (`ERR1`/`WX1`/`WX2`/`WX3`) — `parked-sessions` is process-lifetime, so
  scenarios sharing the Background's `ENG` space would leak park state into
  each other within one `bb jvm-features` run (found this the hard way:
  first cut of these scenarios on `ENG` intermittently swallowed notices
  because an earlier `ENG` scenario had already parked that session).
- `resources/isaac-manifest.edn` — version 0.2.7 → 0.2.8.

**Suites:** `bb lint`, `bb spec` (143 examples), `bb jvm-features` (43
examples), `bb config-bypass-lint` all green after rebasing onto origin/main
(which had moved to 0.2.7 under isaac-qry7 mid-task — rebased and manually
resolved the `on-turn-end*`/`gchat.clj` overlap, verified full `bb ci` green
again). Pushed `bean/isaac-h5v8` to origin. Bean left `in-progress`, no
tags, per instructions.

## Landed on main

main-sha: isaac-gchat 5262798 (0.2.9, rebased over acou 0.2.8 by the planner)

Planner check 2026-09-23: bb spec + bb features 0 failures after the rebase. Agent untouched: the drive already hands on-turn-end :ended-by :provider-unavailable + :reason + :retry-at. Carry-forward (worker flag): weather/resume-suspended! drives a resumed turn with the null comm and no origin, so a sweep-driven resume never reaches gchat; belongs under isaac-ugpq.
