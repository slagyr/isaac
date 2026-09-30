---
# isaac-n8rb
title: turn submit persists a bare-string :session; the wake re-resolution reads it as characters
status: completed
type: bug
priority: high
created_at: 2026-09-30T18:14:06Z
updated_at: 2026-09-30T21:10:08Z
---

Found 2026-09-30 by the foreman restructure (isaac-sb9f). `isaac.agent.turn.submit/submit!` coerces a bare-string `:session` to a vector only for its own submit-time `resolve-session-targets` check, but persists the ORIGINAL frequencies into the durable queue record. The worker's wake-time re-resolution reads that raw `:frequencies` and calls `resolve-session-targets` without the coercion: `(first "lamp-room")` is \\l, so a session named "l" gets created and the turn crashes ("Don't know how to create ISeq from: java.lang.Character").

## Acceptance
- Red first: an agent spec submits with `{:session "lamp-room"}`, ticks, and the turn runs on session lamp-room (fails on current main).
- Fix in agent: the persisted record carries the normalized frequencies (or wake re-applies the same normalization). One normalization, used by both paths.
- Foreman's `normalize-frequencies` workaround (bean/isaac-sb9f worktree) can be dropped afterwards.
- isaac-agent `bb ci` + `bb jvm-spec` green. Ungated.

## Landed on main

Root cause confirmed as traced: `isaac.agent.turn.submit/submit!` coerced
a bare-string `:session` to a vector only for its own submit-time
`resolve-session-targets` precheck, then persisted the ORIGINAL,
uncoerced `frequencies` into the durable queue record. The worker's
wake-time re-resolution (`isaac.agent.turn.worker/wake-charge`) reads
that raw `:frequencies` and calls `resolve-session-targets` without the
coercion, so `(first "lamp-room")` reads the character `\l` instead of
the session id.

Fix: extracted a single `frequencies/normalize` function and moved the
coercion INTO `frequencies/resolve-session-targets` itself, so every
caller — submit-time and wake-time alike, present or future — gets the
same defensive treatment regardless of what shape a durable record
carries. `submit!` also normalizes before persisting (via the same
function), so newly-submitted records are already correct going
forward, and its own local ad hoc coercion was dropped as redundant.

Red specs added:
- `isaac.agent.frequencies-spec` — "treats a bare-string :session the
  same as a one-element vector (isaac-n8rb)" — direct unit coverage on
  `resolve-session-targets`. Failed on main (`Expected: "lamp-room", got:
  nil`), passes with the fix.
- `isaac.agent.turn.submit-spec` — "wakes a turn submitted with a
  bare-string :session onto that session, not a character of it
  (isaac-n8rb)" — full submit! -> tick! round trip with a real (not
  mocked) `resolve-session-targets`. Failed on main with the exact bug
  signature (`Expected: "lamp-room", got: \l`), passes with the fix.

While adding that second spec, found and fixed a pre-existing,
unrelated bug in `submit_spec.clj`: a missing closing paren on "does not
wake a keyed turn twice after the request was accepted" silently nested
the following two `it` blocks inside it, so they never ran as
independent examples (only 10 of the file's 11 originally-written
examples were actually registering). Fixed so all 12 (11 original + 1
new) now run independently; no existing test's behavior changed.

Did not touch the isaac-foreman downstream worktree
(`../isaac-foreman-isaac-sb9f`): it already has substantial uncommitted,
in-progress local changes (bb.edn/deps.edn pin bumps, src/spec edits)
from other work in flight on that bean, and touching it risked
clobbering that state. The `normalize-frequencies` workaround this bean
names is a small, self-contained private fn in `isaac/foreman/core.clj`
— safe for isaac-sb9f's own worker to drop once it repins to this sha.

`bb ci` (native spec+features) and `bb jvm-spec` fully green (spec
1839/1839, incl. the 3 new examples; native features 819/819, 1 pending
as before). `bb jvm-features` full suite: 819 examples, 2 failures — the
same pre-existing timing-sensitive `grover/waiting?` scenarios in
`turn/turn_store.feature` (isaac-2lc4 / isaac-e9jl) seen on unfixed main
while landing isaac-2tez and isaac-n8uv; unrelated to this fix.

main-sha: isaac-agent 7ea663337e9ecdeb110f5412a5d26296e1078aa0

## Planner verification (2026-09-30)

Verified: landed on isaac-agent main with a red-first test; CI green.
