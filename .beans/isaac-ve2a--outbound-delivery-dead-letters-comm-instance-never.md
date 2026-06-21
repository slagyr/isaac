---
# isaac-ve2a
title: 'Outbound delivery dead-letters: comm instance never registered in nexus'
status: todo
type: bug
priority: high
created_at: 2026-06-21T15:40:38Z
updated_at: 2026-06-21T15:40:38Z
---

The delivery worker can't find the live comm, so every outbound reply for
delivery-worker-routed comms (imessage, hail) dead-letters `:permanent` with NO
send attempt, retries, or error. The bot receives + answers; the reply silently
dies. Confirmed on zanebot: 4 failed imessage replies in
`~/.isaac/comm/delivery/failed/` (4ce5, 6b33, 556c, e8dc — all `:comm "imessage"`,
attempts 0). Discord is unaffected (different reply path).

## Root cause (worker trace)
- Delivery worker resolves the live comm via `comm-registry/comm-for`, which reads
  comm instances from the nexus.
- Nothing calls `isaac.comm.registry/register-instance!` in production, so the
  lookup returns nil -> immediate `:delivery/dead-lettered :reason :permanent`.
- The `brth` spec specifies `:register-fn isaac.comm.registry/register-instance!`
  on `:isaac.server/comm` node create + deregister on teardown, but `berths.clj`
  doesn't wire it up.

## Fix
Wire `register-instance!` / `deregister-instance!` into the berth lifecycle for
`:isaac.server/comm` nodes, per the brth spec. (Minimal version of bju6/iiga-3,
which is the bigger comm-Service-lifecycle refactor and is currently blocked.)

## Coordination
- Align with `isaac-bju6` (iiga-3) so its later Service-lifecycle refactor
  subsumes this cleanly rather than colliding. bju6 is blocked (n4dj, kbzd), so
  this targeted fix lands first to restore outbound delivery.

## Acceptance
- A `:isaac.server/comm` node registers its instance in the nexus on create and
  deregisters on teardown.
- The delivery worker resolves the imessage comm and sends via `imsg send` (now
  over the ssh wrapper); reply is delivered, not dead-lettered.
- The 4 stuck records replay/clear (or are documented as lost).

## Deploy
- Lands in whichever repo owns berths.clj; needs a release + zanebot redeploy to
  restore outbound iMessage.
