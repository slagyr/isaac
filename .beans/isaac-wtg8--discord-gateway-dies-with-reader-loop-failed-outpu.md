---
# isaac-wtg8
title: Discord gateway dies with reader-loop-failed "Output closed" and does not recover (heartbeats stop)
status: todo
type: bug
priority: high
tags:
    - discord
    - gateway
    - resilience
    - comms
created_at: 2026-07-03T14:45:13Z
updated_at: 2026-07-03T14:45:13Z
---

## Problem
Discord gateway on zanebot (production server) stops sending heartbeats and never recovers after a specific failure sequence:

- Receives opcode 7 (reconnect)
- Multiple quick disconnects (opcode-7-reconnect + reader-nil-message)
- Reconnects, gets HELLO + READY
- ~12s later: heartbeat-cancelled + `reader-loop-failed` with `java.io.IOException: "Output closed"` (CompletionException)
- After that: **zero** further `:discord.gateway/*` events (no liveness, no heartbeat (even debug), no reconnect-attempt, no new hello/ready)

Other server functions continue (agent turns, hooks, etc.). Connection only comes back after full `isaac service restart`.

This was observed while the server was using the real Tailscale + Discord gateway.

## Symptoms / Reproduction
- Logs show successful reconnect to READY, then abrupt transport death.
- No more scheduled heartbeats or liveness checks.
- `gateway/connected?` would return false; no messages flow to the "isaac" channel.
- Reproduce in tests by forcing "Output closed" in the reader path (similar to existing heartbeat-send test) after an opcode-7 reconnect.

## Root Cause (from code dig)
Primary paths in `isaac-discord/src/isaac/comm/discord/gateway.clj`:

- Reader loop (`start-reader-loop!` future) catches exceptions and calls `on-close!` with `{:reason "reader-loop-failed" :status 1006}`.
- `on-close!` cancels heartbeat task, closes transport, then `attempt-reconnect!` → `schedule-reconnect!` (one-shot `:delay` using hardcoded id `:discord.gateway/reconnect`, `:on-error :retry`, MAX attempts).
- Reconnect handler logs `reconnect-attempt` then `do-reconnect!` (new WS + eager RESUME/IDENTIFY + reader + heartbeats on HELLO).
- Guard using `:auth-sent?` prevents duplicate auth on reconnected HELLO (fix for old "isaac-ceeq" heartbeat death).
- `{:error ...}` messages from the WS queue are only logged (`:discord.gateway/error`), not turned into `on-close!` (just `recur`).
- `schedule!` / `cancel!` for the reconnect id are non-atomic. Multiple near-simultaneous `on-close!` (common in disconnect storms) + scheduler's own swaps can cause "task already scheduled" throw inside `on-close!` (called from reader future catch → exception lost, no task registered).
- One-shot delay reconnect tasks sometimes left in scheduler tasks atom after handler (race in `finish-run!` / `done?` / `compute-finish-transition` for `:delay`).
- Outer layer (`service.clj` `reconcile-registration!` + `DiscordIntegration`) sees the (stale) gateway client object as "current" so never forces a fresh top-level `connect!`. Recovery is 100% internal.
- Heartbeat `every!` and reconnect use the nexus scheduler (or passed one). If scheduling silently fails, heartbeats never restart.
- "Output closed" commonly surfaces on `.join` of `sendText` or receive paths when the Java WebSocket channel dies (tested in specs for send case; reader path less defended).
- No outer watchdog / max-stale timer that would force reconnect or log a clear "Discord comm is dead, restarting client" at service level.

After the final `on-close!` the reconnect task was never successfully (re)registered or its handler never produced visible events again.

## Scope
Affects `isaac.comm.discord` gateway resilience (both initial connect and post-reconnect recovery). Impacts production bots using real Discord gateway over Tailscale / unstable networks.

Not a full server crash — just the comm goes silent.

## Acceptance Criteria (runnable)
- [ ] Add / extend spec (gateway_spec.clj or new) that reproduces the exact observed sequence: opcode-7 disconnect wave + reader "Output closed" (CompletionException) after READY. Assert:
  - Exactly one clean reconnect attempt fires after the reader failure.
  - Recovery reaches READY again.
  - Heartbeat / liveness tasks are rescheduled and fire.
  - No "task already scheduled" or lost exceptions.
- [ ] In the test, after recovery, `connected?` is true and a simulated MESSAGE_CREATE is accepted (proves heartbeats + reader are alive).
- [ ] Reader loop treats `{:error ...}` from the queue as a close trigger (calls `on-close!` with the error) instead of just logging + recur.
- [ ] Reconnect task scheduling uses a more robust id or atomic ensure-or-replace (no throw on re-schedule during storm).
- [ ] Add a simple liveness watchdog in `DiscordService` / registration: if not `connected?` for > N minutes (e.g. 5), force a client reconnect (or full re-registration) and log at WARN.
- [ ] Existing reconnect tests (opcode 7, 9, 1006, heartbeat-ack-timeout, dead-socket) still pass.
- [ ] On zanebot-like failure, a fresh `isaac service restart` is no longer required; internal recovery succeeds within one backoff window.
- [ ] Document the observed "Output closed" + reader failure mode and the recovery expectations.

## Suggested Fix Approach
1. Harden `receive-text!` / reader to treat queue errors as disconnect.
2. Make `schedule-reconnect!` idempotent / "ensure scheduled" (cancel + schedule, or use a different pattern for pending reconnects).
3. Add outer service-level stale check (lightweight, using existing `connected?` + scheduler).
4. Consider surfacing a `force-reconnect!` or making the comm factory more resilient to dead transports.
5. Increase visibility: promote some post-recovery logs or add a "discord gateway recovered after X attempts" info event.

## Notes
- Ties into broader comm resilience (see past isaac-ceeq work on auth/heartbeat death).
- "Output closed" is a symptom of underlying WS death (Discord side, Tailscale, Java client); the bug is lack of guaranteed recovery.
- Scheduler one-shot + retry logic for reconnect is the critical path that got stuck.
