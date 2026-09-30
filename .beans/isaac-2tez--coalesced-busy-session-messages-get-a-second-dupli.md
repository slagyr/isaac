---
# isaac-2tez
title: Coalesced busy-session messages get a second, duplicate turn (drain tick races finish-marking)
status: completed
type: bug
priority: high
created_at: 2026-09-30T17:56:19Z
updated_at: 2026-09-30T21:10:08Z
---

Found 2026-09-30 by the gchat restructure (isaac-fstx). Scenario: three quick messages in one DM thread (gchat features, isaac-xoqn) should get ONE consolidated reply; against agent 123d718 they get two.

Trace (worker's): isaac-agent `bridge/core.clj`'s drain-waiting-session cleanup fires a nested `tick!` from inside the FIRST coalesced turn's `dispatch!`, before that turn's `process-record!` has marked the trailing coalesced members `:finished`. Since isaac-e9jl/isaac-2lc4 moved finish-marking after `dispatch!` returns (async worker), the nested tick re-discovers messages 2 and 3 as `:waiting-session` and runs them as a second, spurious turn. Pre-e9jl, finish-marking was inline and the race couldn't happen.

Production impact: any comm (gchat, discord, imessage…) on an agent with e9jl may double-reply to bursts of messages.

## Acceptance
- Red first: an isaac-agent spec or feature that coalesces several waiting messages for one busy session and asserts exactly one turn runs for them (fails on current main).
- Fix: coalesced members are marked finished before any drain tick can see them (or the drain skips members of an in-flight coalesced turn); no sleeps.
- isaac-gchat branch `bean/isaac-fstx` (worktree ../isaac-gchat-isaac-fstx) goes 60/60 on `bb jvm-features` when pinned to the fixed agent.
- Full isaac-agent `bb ci` + `bb jvm-spec` green.

Ungated; planner verifies. Agent main is on the post-restructure namespaces (`isaac.agent.*`).

## Landed on main

Root cause confirmed exactly as traced: `isaac.agent.turn.worker/run-one-pass!`
claimed only the coalesced group's own merged-record id, leaving trailing
coalesced members at `:waiting-session`. A nested `tick!` (bridge.core's
own-session drain-waiting-session, fired from inside that same coalesced
turn's `dispatch!` — after `clear-in-flight!` but before `process-record!`
marks the group `:finished`) rediscovered those trailing members and started
a second, spurious turn for them.

Fix: `run-one-pass!` now claims every id in a coalesced group up front
(before starting the async turn), so a nested drain sees nothing left in
`:waiting-session` to pick up. `process-record!`'s still-held branch was
also updated to revert every held-id (not just the primary) back to
`:held`, keeping the claim/unclaim symmetric for a group that ends up
parked again.

Red spec added: `isaac.agent.turn.worker-spec` — "does not run a duplicate
turn for a coalesced member exposed by a nested drain (isaac-2tez)" —
mocks `bridge/dispatch!` to fire a nested `tick!` mid-dispatch (mirroring
bridge.core's real finally-block drain) and asserts only one dispatch call
happens for the merged input. Failed on main (`["two\nthree" "three"]`,
duplicate), passes with the fix.

Verified against the gchat downstream repro (`../isaac-gchat-isaac-fstx`,
`features/comm/gchat/inbound.feature:700`, isaac-xoqn) via a temporary
`:dev-local` deps.edn edit (reverted, nothing committed there): the
isaac-xoqn scenario failed on unfixed agent (`Expected: 1, got: 2`
outbound HTTP requests), passed after the fix; full gchat `bb
jvm-features` via `:dev-local` then went 60/60, matching this bean's
acceptance criterion.

`bb ci` (native spec+features) and `bb jvm-spec` fully green. `bb
jvm-features` full suite: 818 examples, 2 failures — both timing-sensitive
`grover/waiting?` assertions in `turn/turn_store.feature` (`isaac-2lc4`'s
"server's own tick" scenario and isaac-e9jl's "different sessions run side
by side" scenario), reproduced identically on unfixed main — pre-existing
flake, unrelated to this fix (not the coalescing scenario at line 116,
which passed both isolated reruns).

main-sha: isaac-agent 7e81a236366c00215910201614e0572e41898cad

## Planner verification (2026-09-30)

Verified: landed on isaac-agent main with a red-first test; CI green.
