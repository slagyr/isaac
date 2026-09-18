---
# isaac-lsz2
title: 'Module repos pin sibling bean-branch shas that verify squashes away: isaac-cli-server CI red (foundation 3963266), isaac-server agent pin unreachable'
status: todo
type: bug
priority: high
tags:
    - ci
created_at: 2026-09-18T04:46:20Z
updated_at: 2026-09-18T04:46:20Z
---

## Problem

isaac-cli-server main CI has been red since isaac-qvhy landed (02:25Z): `Error building classpath. Commit not found for marigold.longwave … isaac-foundation.git at 3963266`. The worker pinned foundation at the sha of the sibling bean branch (1fwl's foundation leg); verify then squash-merged that branch into a different sha and deleted it, so the pin points at nothing GitHub serves. isaac-server has the same disease: its `:test` alias pins isaac-agent `b6284e42` (from the 09-11 zgfx bean), unreachable — CI is green there only because the runner's gitlibs cache still holds the object; a fresh clone cannot build.

Sweep (2026-09-18, every module repo's deps.edn + bb.edn, pins to isaac-* repos):

| repo | pin | reachable? |
|---|---|---|
| isaac-cli-server | foundation `3963266` (bb.edn ×3, deps.edn) | **no** — CI red |
| isaac-server | agent `b6284e42` (`:test` alias, bb.edn + deps.edn) | **no** — CI green by cache only |
| everything else | — | yes |

## Fix (this bean; planner-implemented)

1. isaac-cli-server: repin foundation (test-support, marigold.*, foundation) to foundation **main** `e4da6e0` (carries 1fwl/qvhy/kjzq). Surfaced one racy spec: `dispatch_spec.clj` "runs a read-only hosted command when the loaded basis is stale" exited its `-with-nexus` scope before the hosted task ran (the task is a future that reads the nexus) — hold the scope until the exit frame. `bb spec` 5/5 green after.
2. isaac-server: repin the `:test` alias agent to agent main (`0e804c0` or newer). **Worker leg** — planner tried it: `bb spec` green (125), `bb features` has 4 failures against agent main (Comm extension "Multiple comm instances of the same :type coexist"; Module activation "Comm slot starts when configured at boot", "Declared module is activated during server boot even without a slot", "Module activation failure surfaces a structured error") — a week of agent changes since b6284e4; fix the features/steps for the current agent, don't pin backwards.
3. **Rule (add to isaac/AGENTS.md bean workflow + the verify checklist):** a bean may only pin a sibling repo at a sha reachable from that repo's `main` (`git branch -r --contains <sha>` includes `origin/main`). Never a bean-branch sha — verify squashes and deletes it. Verify rejects a handoff whose pins fail this check.

## Also observed (not fixed here)
isaac-cli-server `features/cli/endpoint.feature` "a reattached client receives frames buffered while detached (isaac-qvhy)" is flaky (~1 in 3 locally): the echo's two output writes ("while away", "\n") race the attach replay, and the frame matcher sees "\n" first. Needs its own look (buffer/replay ordering in dispatch, or the scenario's stdin-while-detached step) — bean it if it bites CI.

## Handoff

- isaac-cli-server `bean/isaac-lsz2` @ 5d54b56 (repin + racy spec held open; `bb spec` 5/5 green, `bb features` green except the pre-existing reattach flake noted above). Planner-implemented.
- isaac-server leg: not started — worker.

## Acceptance
```
cd isaac-cli-server && bb ci        # green on main after merge
cd isaac-server && bb ci            # green with the agent repin
```
CI green on both repos' main; the sweep above re-run shows no unreachable pins.


## Structural fix

The root cause (in-flight cross-repo pins that verify squashes away) is **isaac-j4jr**: verify repins before merging, `bb lint-pins` in every `bb ci`, `:dev-local` while in flight. This bean stays the symptom fix; the rule in "Fix 3" above is superseded by that bean.
