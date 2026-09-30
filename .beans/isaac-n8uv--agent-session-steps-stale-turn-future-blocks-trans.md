---
# isaac-n8uv
title: 'agent session steps: stale :turn-future blocks transcript checks on a still-parked session'
status: completed
type: bug
priority: high
created_at: 2026-09-30T17:58:47Z
updated_at: 2026-09-30T21:10:08Z
---

Found 2026-09-30 by the worksite restructure (isaac-6uou). isaac-agent's test support (`isaac.agent.session.session-steps`, shipped in isaac-agent-spec): `user-sends-on-session` now sets the single `:turn-future` slot only when nothing is already parked. After a second session's turn ends, `:turn-future` still points at a different, intentionally parked session's future, so asserting the first session's transcript (`await-turn!` / `await-transcript-turn!`) blocks 30s and fails.

Repro: isaac-worksite branch `bean/isaac-6uou` (worktree ../isaac-worksite-isaac-6uou), `features/worksite/lock.feature` "two members run two turns; a third waits…": 1/8 fails on agent 123d718, passes 8/8 in ~3.6s on the old pins.

## Acceptance
- Red first: an isaac-agent spec/feature where two sessions each send, one is parked, the other's turn ends, and the other session's transcript check returns promptly (fails on current main).
- Fix: turn futures are tracked per session (or the await resolves the right session's future); no sleeps, no longer timeouts.
- isaac-worksite `bean/isaac-6uou` goes 8/8 on `bb jvm-features` pinned to the fixed agent.
- isaac-agent `bb ci` + `bb jvm-spec` green.

Ungated; planner verifies.

## Landed on main

Root cause confirmed as traced: `await-transcript-turn!` (isaac-agent test
support, `isaac.agent.session.session-steps`) special-cased "this
session's per-session future equals the single legacy `:turn-future`
slot" as a signal that the session was the one under test and delegated
to `await-turn!`'s unconditional 30-second block. That equality is only
an accident of which pending send filled the legacy slot first — with
two sessions parked concurrently (isaac-e9jl), checking the OTHER
(still-intentionally-parked) session's transcript hung 30s and failed.

Fix: `await-transcript-turn!` now skips the block only when the
session's own future is unrealized AND still actively wait-gated
(`grover/waiting?`) — nothing is going to release it as part of a
transcript read. Otherwise it derefs with the existing 30s safety
timeout, unifying the two previously-separate branches into one and
still completing the primary turn's bookkeeping (`complete-turn!`) when
the future matches the legacy slot.

Red scenario added: `features/session/waiting.feature` — "a session's
transcript check does not block on a different session's still-parked
turn (isaac-n8uv)" — two sessions each send with a `wait: true` scripted
response, one session's turn ends, then the OTHER (still-parked)
session's transcript is asserted. Failed on main ("turn did not complete
within 30 seconds", 31s), passes in ~1.3s with the fix.

Verified against the isaac-worksite downstream repro
(`../isaac-worksite-isaac-6uou`, `bean/isaac-6uou`, `features/worksite/lock.feature`)
via a temporary `:dev-local` deps.edn edit (reverted, nothing committed
there): full worksite `bb jvm-features` via `:dev-local` went 8/8,
matching this bean's acceptance criterion.

`bb ci` (native spec+features) and `bb jvm-spec` fully green (spec
1836/1836; native features 819/819 incl. the new scenario, 1 pending as
before). `bb jvm-features` full suite: 819 examples, 2 failures — the
same pre-existing timing-sensitive `grover/waiting?` scenarios in
`turn/turn_store.feature` (isaac-2lc4's "server's own tick" and
isaac-e9jl's "different sessions run side by side") seen and reproduced
on unfixed main while landing isaac-2tez; unrelated to this fix, and the
new isaac-n8uv scenario is not among the failures.

main-sha: isaac-agent dc2e37174096b7e02b844362c9a3934e11f62c9d

## Planner verification (2026-09-30)

Verified: landed on isaac-agent main with a red-first test; CI green.
