---
# isaac-x3g4
title: A worksite acquisition that fails leaves its lock behind; one Foreman turn leased all four members
status: in-progress
type: bug
priority: high
created_at: 2026-10-06T20:23:24Z
updated_at: 2026-10-06T20:56:35Z
---

Likely repos: **isaac-agent** (turn worker) and/or **isaac-worksite** (lock guard). Found by
Foreman pilot 1 (isaac-8uno on zanebot), 2026-10-06. No planner scenario: reproduce first.

## What happened

One Foreman work turn (`0fa9c4e4`, pool `isaac-worksites`, 4 members) was submitted from inside
Prowl's `foreman__signal` tool call; Foreman then called `worker/tick!` inline. All four
worksite locks were written by the server (pid 28583) within 0.4 ms of each other:

    work-1 19:05:15.583023  work-2 .583196  work-3 .583304  work-4 .583395

No `:turn.queue/woke` was ever logged for the turn, and no warn/error. Every later tick found
the pool busy and kept it held. `turns drop` finished the record but freed nothing; the locks
are still on disk (`~/.isaac/worksites/*.lock`, holder `bean-isaac-8uno`). A restart frees them
(the owner pid dies → stale → stolen).

## What we know

- 100 µs apart in one call means ONE `try-acquire` walked all members: each
  `lock/acquire-turn!` wrote its lock yet reported not-ok, so `some` moved on and the pool
  answered `:busy`. `with-guard`'s RealFs branch turns ANY exception into "busy" silently
  (`(catch Exception _ busy)`), so a throw after `write-lock!` (or from `.release`) would look
  exactly like this.
- The pool and lock work in isolation on a real filesystem (planner check, 2026-10-06,
  including an interrupted thread). The feature suite runs on MemFs, which skips the RealFs
  guard branch entirely.

## Acceptance

- A spec reproduces the leak (real fs; whatever throws inside the guard), then passes.
- A worksite acquisition never reports busy/failed while leaving a lock it wrote; the swallowed
  exception is logged (`:worksite/…` warn with the message).
- Anything that throws between lease acquisition and the turn claim in the queue worker releases
  the leases it took (`claim-and-start!` / `admit!`).
- isaac-worksite and isaac-agent features stay green.

## Work checkpoint (2026-10-06)

Done: RealFs failed-write regression and cleanup/warn implemented in isaac-worksite (d98a5d3); queue claim/admit lease release regressions implemented in isaac-agent (5e2ec86). Both focused specs green; worksite `bb ci` green. Branches pushed.
Next: run agent `bb ci`, inspect failures, refine RealFs guard cleanup (especially release exceptions), then gate and ungated verify handoff. Resume at `isaac-agent/src/isaac/agent/turn/worker.clj:249` and `isaac-worksite/src/isaac/worksite/lock.clj:110`.

## Verification handoff (2026-10-06)

Worksite `bb ci` green (23 specs, 8 feature examples). Agent focused worker specs green (23 examples) and `bb features` green (875 examples, 1 pre-existing pending). Agent `bb ci` stops in `bb spec`: `session_steps_spec.clj:71` raises "no filesystem available"; reproduced identically on clean agent `origin/main` (25a2fef) in a detached worktree. No changes to unrelated test. `bb bean-gate verify isaac-x3g4` exited 2: no feature-baseline. Branches: isaac-worksite d98a5d3; isaac-agent 5e2ec86. Next: verifier review and landing; baseline suite failure needs a separate repair.



## Verify fail (attempt 1, 2026-10-06): a partial write still leaves a real-filesystem lock after failed acquisition

HEAD: isaac-worksite d98a5d3; isaac-agent 5e2ec86 (both origin/bean/isaac-x3g4).
Working trees: clean detached verification worktrees.

Reproduction on worksite branch: `bb -e '(require (quote [isaac.foundation.fs :as fs]) (quote [isaac.foundation.nexus :as nexus]) (quote [isaac.worksite.lock :as lock])) (let [root (str (java.nio.file.Files/createTempDirectory "x3g4-partial-" (make-array java.nio.file.attribute.FileAttribute 0))) spit* fs/spit] (nexus/-with-nexus {:fs (fs/real-fs) :root root} (let [result (with-redefs [fs/spit (fn [f p _] (spit* f p "{:kind :turn") (throw (ex-info "disk interrupted" {})))] (lock/acquire-turn! root "room" {:session-key "harbor"}))] (println result (fs/exists? (fs/real-fs) (lock/lock-path root "room"))))))'` prints `{:error :already-locked} true`. `undo-written-lock!` at lock.clj:103-106 skips partial/unparseable locks because `read-lock` returns nil. The newly added spec writes the complete valid record before throwing, so it misses this failure. Fix cleanup to remove only the lock written by this acquisition, including partial/corrupt writes, without deleting a pre-existing lock; add real-fs regression for partial write and verify lock-path absence. Branch `bb ci` otherwise green: worksite 23 specs / 8 features, agent 1889 specs / 875 features (1 existing pending). No feature-file edits. Not landed on main.

## Gated (planner, after verify fail 1)

Now gated: isaac-worksite `features/worksite/lock.feature:145` — a lock write that fails partway
leaves no lock; the turn takes the next member. Runs on a real directory. New steps (worksite
feature-steps): `the worksite locks live on the real filesystem` and
`writing the lock for "<member>" fails partway` (write a partial record, then throw — the
verifier's repro above). Rebase bean/isaac-x3g4 on worksite main to pick it up. With the
baseline, gate exit 0 lands it; no verify hail.

feature-baseline: isaac-worksite 8dd8e12526c6ab2f6a68829bc2c747e10449d17f
feature-blob: isaac-worksite features/worksite/lock.feature 96ee6ee2e4d21daedb0ee883911c576fdec9a6a8 145
