---
# isaac-v3qp
title: 'Foreman live fixture: ungated round trip with one planned repair (isaac-q6fj, rerun)'
status: completed
type: task
priority: normal
created_at: 2026-10-28T07:58:41Z
updated_at: 2026-10-28T08:06:48Z
parent: isaac-q3u3
---

A deliberate test bean (rerun of scrapped isaac-pqxn, after Foreman 0.6.1) for isaac-q6fj's live acceptance: one ungated bean through the Foreman machine, with one planned repair. The deliverable is small but real.

## Ungated

orchestration has no feature runner for docs, so there is no gate. Ungated path: the worker ends with `event: unverified` and the verifier decides.

## Work

Add `isaac-beans/features/README.md` to slagyr/orchestration (on `bean/isaac-v3qp`, from origin/main). Three or four plain sentences:
- what the `.feature` files in this folder are (Foreman machine tests for `config/machines/isaac-bean-work.edn`)
- that they run with `isaac foreman test <file>` against an Isaac root that has the Foreman module, the crews the machine names, and the `isaac-worksites` pool
- that machine tests carry no instance data, so `{{data.*}}` renders empty in them

Don't touch README.md, bb.edn, install.sh or anything under config/: isaac-q6fj's branch changes those.

## Verifier: planned repair

This bean tests the repair path. **On the first verification**, don't land it: append a `## Repair 1` note asking the worker to add one sentence saying the tests use a fixed step vocabulary provided by Foreman. Then end with `event: repair fixture: planned first-pass repair (isaac-q6fj)`.

**On any later verification**, verify normally against the Acceptance below, which includes the Repair 1 sentence, and land it.

## Acceptance

- [ ] `isaac-beans/features/README.md` exists on orchestration main and covers the three points under Work, plus the Repair 1 sentence.
- [ ] No other orchestration files changed.
- [ ] `isaac foreman status isaac-bean-work isaac-v3qp` history shows working → verifying → working (repair) → verifying → landed.


## Process Observations (2026-10-28)

Added `isaac-beans/features/README.md` on orchestration `bean/isaac-v3qp` at `4e7bbf7` (pushed). It covers the machine fixture, test invocation and required Isaac root, and empty instance-data templates. Only that file changed; `git diff origin/main --check` passed. `bb bean-gate verify isaac-v3qp` exited 2: no feature-baseline; handoff to the first verifier for the planned Repair 1 request.



## Repair 1 (2026-10-28)

Planned first-pass repair per the bean: add one sentence to `isaac-beans/features/README.md` stating that the tests use a fixed step vocabulary provided by Foreman. Current `origin/bean/isaac-v3qp` commit `4e7bbf75acf0d957c76b95ef4f85aa8aac355f73` has only the three Work points; the sentence is absent. Keep the change limited to this README, push the branch, and hand back for verification. Do not land yet.

## Verification failed (2026-10-28)

HEAD: isaac `d7056e85` (origin/main); orchestration review `4e7bbf75acf0d957c76b95ef4f85aa8aac355f73` (origin/bean/isaac-v3qp).
Working tree: both review worktrees clean.
Reason: mandatory planned Repair 1 on first verification; README lacks the fixed-step-vocabulary sentence required by Acceptance. Only `isaac-beans/features/README.md` changed; `git diff origin/main...HEAD --check` passed. Acceptance tests and Foreman history intentionally deferred until repair.



## Repair 1 implemented (2026-10-28)

Added the requested fourth sentence about Foreman’s fixed step vocabulary in orchestration `isaac-beans/features/README.md`; commit `94daba8bbc685cf3e86548e441b03acdfe8dc685` pushed to `origin/bean/isaac-v3qp`. `git diff --check` passed; only that README differs from `origin/main`. `bb bean-gate verify isaac-v3qp` exited 2 (no feature-baseline). Awaiting second verification; no landing by worker.



## Landed on main (2026-10-28)

main-sha: orchestration 18c649ca00e7312f9806c54d05286a53f1185cbe

Verified: the four required README sentences are present; only isaac-beans/features/README.md changed; diff --check clean. Foreman machine fixture: 16 PASS, 0 failures. Live history shows working → verifying → working (repair) → verifying; final landed transition follows the verified verdict.
