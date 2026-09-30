---
# isaac-8evx
title: Retry wake of a pool-held turn resolves its resource pool as unknown
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-30T18:14:06Z
updated_at: 2026-09-30T21:08:54Z
---

Found 2026-09-30 by the foreman restructure (isaac-sb9f). A turn held by a closed resource pool: the first wake correctly resolves the pool (known, closed). A later wake via `foreman retry` (which loads a fresh config snapshot and calls `isaac.agent.turn.worker/tick!`) resolves the same pool as `:unknown-resource-pool`, with the same pool file on disk. Foreman scenarios in features/foreman/turn_action.feature: "a refused submission stays pending…" and "a retry after Foreman lost the request id…" fail on agent 123d718 and pass on the old pins. Root cause not yet traced (resource-pool resolution on the wake path vs the config snapshot tick! uses).

## Acceptance
- Red first: an agent-level scenario/spec where a pool-held turn is woken twice (second wake from a freshly loaded config, as a CLI would) and the pool resolves as known both times.
- Fix in agent; no sleeps.
- isaac-foreman bean/isaac-sb9f goes green on those two scenarios when pinned to the fixed agent.
- isaac-agent `bb ci` + `bb jvm-spec` green. Ungated.

## Landed on main

Root cause traced: `isaac.agent.turn.worker/wake-charge` called
`wake-config` (reading ambient config via `loader/snapshot`, falling back
to `loader/load-config!`) lazily, inside `process-record!` — which
isaac-e9jl moved onto the claimed turn's own future thread ("claim and
start, then return"). `isaac.foundation.nexus` is a single, plain,
process-wide atom (`root-runtime`), not a dynamic var — `isaac.foundation.main/run`
wraps every CLI command in `(nexus/-with-nested-nexus {:fs fs*} (nexus/init!
{:fs fs* :root ...}) ...)`, and `nexus/init!` unconditionally replaces
`:config` with a brand-new atom on every invocation. A caller like
`isaac.foreman.core/retry!` (load a fresh config, then call `tick!`,
exactly as a CLI would) returns — unwinding that nested scope, discarding
the atom `init!` just installed and whatever `load-config!` put in it —
before the turn's own future thread ever gets to run, since `tick!` only
claims and starts it before returning. That later, asynchronous read can
then see whatever the now-restored outer scope holds instead — including
nothing — and resolve a resource pool the caller's own freshly-loaded
config plainly declares as `:unknown-resource-pool`.

Fix: `run-one-pass!` now resolves `wake-config` synchronously, on the
`tick!`-calling thread, before starting the claimed record's future, and
threads the resolved config through `run-record-async!` ->
`process-record!` -> `wake-charge` as a plain argument, instead of each
future re-deriving it independently. Considered wrapping the future in
`isaac.foundation.nexus/bound-runtime-fn` (capturing and reinstalling the
*whole* nexus snapshot on the turn's thread) as a more complete fix, but
nexus is one shared, global, non-thread-safe atom: reinstalling it on one
turn's thread while other turns run concurrently on their own threads
(the whole point of isaac-e9jl) would race them — a turn's admission
could transiently see the config another concurrently-running turn just
installed, or nothing, depending on scheduling. Threading only the one
value admission actually needs (config) avoids that: it's a plain
immutable value, no shared mutable state touched, no cross-turn race.

Red spec added: `isaac.agent.turn.worker-spec` — "resolves wake-config
synchronously, before tick! returns, so a caller's own nexus scope ...
has not yet moved on when the config is read (isaac-8evx)" — asserts
`loader/snapshot` is read before `tick!` returns, not after. This
directly tests the architectural property described above (synchronous
vs. deferred-to-another-thread config resolution) rather than trying to
reproduce the exact ambient-nexus-loss mechanism in a unit test, which
proved both hard to isolate deterministically and, once isolated,
overlapped with a separate, pre-existing, out-of-scope limitation:
`isaac.agent.turn.queue`'s own file operations *also* read ambient
`isaac.foundation.fs/instance` (nexus `:fs`) rather than a captured
value, so a test that also strips `:fs`/`:root` between the two waves
hits that limitation first (a "no filesystem available" throw) — a
real, but separate and older, gap in queue.clj, not part of this bean's
resource-pool scope. Failed on main (`:after-tick-returned`), passes
with the fix (`:before-tick-returned`).

Did not touch the isaac-foreman downstream worktree
(`../isaac-foreman-isaac-sb9f`): it already has substantial uncommitted,
in-progress local changes (bb.edn/deps.edn pin bumps, src/spec edits)
from other work in flight on that bean, predating this session, and
touching it risked clobbering that state (also true for isaac-n8rb's
downstream note). Ran the two named scenarios there via `clojure
-M:features:dev-local` against the *unfixed* agent pin (its existing
`../isaac-agent` default) for orientation only: "a refused submission
stays pending..." and "a retry after Foreman lost the request id..."
both fail, but on an assertion inside that worktree's own dirty state
that doesn't cleanly isolate to just this bug (see above) — I could not
get a clean pass/fail signal there without editing files outside this
bean's scope, so I did not repoint dev-local at the fixed worktree for a
before/after comparison. Recommend isaac-sb9f's own worker re-verify
against this fix from a clean foreman checkout.

`bb ci` (native spec+features) and `bb jvm-spec` fully green (spec
1840/1840, incl. the new example; native features 819/819, 1 pending as
before). `bb jvm-features` full suite: 819 examples, 2 failures — the
same pre-existing timing-sensitive `grover/waiting?` scenarios in
`turn/turn_store.feature` (isaac-2lc4 / isaac-e9jl) seen throughout this
session while landing isaac-2tez, isaac-n8uv, and isaac-n8rb; unrelated
to this fix.

main-sha: isaac-agent f9530426d04b6f66f17ae51f6f9a1a697531b39d
