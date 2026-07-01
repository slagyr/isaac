---
# isaac-ve2a
title: 'Outbound delivery dead-letters: comm instance never registered in nexus'
status: completed
type: bug
priority: high
created_at: 2026-06-21T15:40:38Z
updated_at: 2026-06-21T16:00:32Z
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

## Worker notes (work-2, 2026-06-21)

- `isaac-foundation` @ `c48fb06`: berths reconcile invokes berth `:register-fn` / `:deregister-fn` on factory node create/teardown; maps `[:comms]` slots via `:dynamic-schema :berth`.
- `isaac-server` @ `ea8706d`: `:isaac.server/comm` berth declares `isaac.comm.registry/register-instance!` + deregister.
- `isaac-agent` @ `0cebe7a`: test fixture berth mirrors register-fn.
- `isaac-imessage` @ `a56e4d9`: lifecycle spec asserts `comm-registry/comm-for "imessage"` after server boot + deregister on slot delete.
- Tests: foundation `bb ci` (762+117 ex), `bb spec berths_spec` (13 ex), imessage `clojure -M:dev-local:spec imessage_lifecycle_feature_spec` (35 ex), agent delivery worker spec (6 ex) — 0 failures.
- Deploy: foundation + server releases + zanebot redeploy required; 4 stuck `~/.isaac/comm/delivery/failed/*.edn` records are lost (attempts 0, never sent).

## Scenarios (DRAFT — pending review; do not generate feature file yet)
```gherkin
Scenario: a comm node registers its live instance when its berth node is created
  Given a configured :isaac.server/comm slot (e.g. imessage)
  When the server boots and the comm node is created
  Then isaac.comm.registry/comm-for resolves the live comm instance from the nexus

Scenario: outbound delivery reaches the comm instead of dead-lettering
  Given a registered imessage comm
  And  a queued outbound reply addressed to that comm
  When the delivery worker processes the record
  Then it resolves the comm and invokes send!
  And  the record is NOT dead-lettered with :reason :permanent

Scenario: teardown deregisters the comm instance
  Given a registered comm instance
  When the comm node is torn down
  Then isaac.comm.registry/comm-for no longer resolves it
```
(Note: the middle scenario — delivery-worker resolves the comm — likely lands as
a unit/integration spec on the worker + registry; the register/deregister pair
is the brth-lifecycle feature. Split during speccing.)
