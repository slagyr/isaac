---
# isaac-lsz2
title: 'Fleet-wide dangling pins: 11 module repos pin foundation at a squashed bean-branch sha (ad0a97b, bean/isaac-1fwl) — 5 repos'' CI red; repin everything to main'
status: in-progress
type: bug
priority: critical
tags:
    - ci
created_at: 2026-09-18T04:46:20Z
updated_at: 2026-09-18T23:54:17Z
---

## Problem

isaac-cli-server main CI has been red since isaac-qvhy landed (02:25Z): `Error building classpath. Commit not found for marigold.longwave … isaac-foundation.git at 3963266`. The worker pinned foundation at the sha of the sibling bean branch (1fwl's foundation leg); verify then squash-merged that branch into a different sha and deleted it, so the pin points at nothing GitHub serves. isaac-server has the same disease: its `:test` alias pins isaac-agent `b6284e42` (from the 09-11 zgfx bean), unreachable — CI is green there only because the runner's gitlibs cache still holds the object; a fresh clone cannot build.

Sweep (2026-09-18, every module repo's deps.edn + bb.edn, pins to isaac-* repos):

| repo | pin | reachable? |
|---|---|---|
| isaac-cli-server | foundation `3963266` (bb.edn ×3, deps.edn) | **no** — CI red |
| isaac-server | agent `b6284e42` (`:test` alias, bb.edn + deps.edn) | **no** — CI green by cache only |
| isaac-agent | foundation `1c8e45b` (bb.edn ×5, deps.edn) | **only via leftover branch `bean/isaac-t1om`** — a time bomb: the moment that branch is deleted, agent main stops building from a cold cache (isaac-2yh9, 2026-09-15, folded in here) |
| everything else | — | yes |

The reachability test is **reachable from `origin/main`** (`git merge-base --is-ancestor <sha> origin/main`), not "on any remote branch" — the first sweep used the weaker test and missed the agent case.

## Fix (this bean; planner-implemented)

1. isaac-cli-server: repin foundation (test-support, marigold.*, foundation) to foundation **main** `e4da6e0` (carries 1fwl/qvhy/kjzq). Surfaced one racy spec: `dispatch_spec.clj` "runs a read-only hosted command when the loaded basis is stale" exited its `-with-nexus` scope before the hosted task ran (the task is a future that reads the nexus) — hold the scope until the exit frame. `bb spec` 5/5 green after.
2. isaac-agent: repin foundation (bb.edn ×5 + deps.edn) to foundation main; `bb spec && bb features`. Known follow-up: `spec/isaac/config/agent_steps.clj:7` requires `isaac.startup.config-cache`, a namespace that exists ONLY on the leftover `bean/isaac-t1om` foundation branch (never merged; foundation main has `isaac.startup.cache` / `classpath-cache`) — this breaks every downstream repo's dev-local feature run today (hail, discord: `FileNotFoundException … isaac/startup/config_cache`). Port the steps to the main namespace.
3. isaac-server: repin the `:test` alias agent to agent main (`0e804c0` or newer). **Worker leg** — absorbs isaac-ane7 (Lifecycle reconciler 'Two comms run independently when both slots are present at boot' under dev-local — same server-vs-current-agent family; scrapped into here). Planner tried it: `bb spec` green (125), `bb features` has 4 failures against agent main (Comm extension "Multiple comm instances of the same :type coexist"; Module activation "Comm slot starts when configured at boot", "Declared module is activated during server boot even without a slot", "Module activation failure surfaces a structured error") — a week of agent changes since b6284e4; fix the features/steps for the current agent, don't pin backwards.
4. **Rule (add to isaac/AGENTS.md bean workflow + the verify checklist):** a bean may only pin a sibling repo at a sha reachable from that repo's `main` (`git branch -r --contains <sha>` includes `origin/main`). Never a bean-branch sha — verify squashes and deletes it. Verify rejects a handoff whose pins fail this check.

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


## Re-scoped to the fleet (planner, 2026-09-18 23:50Z) — CRITICAL

The 1fwl worker's foundation branch head `ad0a97b7f039814dc92916299baf3c07a5b86f3a` was pinned by nearly every module repo during the 09-18 pin-bump wave; verify squashed isaac-1fwl to `cc53d69` and the branch `bean/isaac-1fwl` survives only by accident. It is on NO main. Current CI:

| repo | CI | pin problem |
|---|---|---|
| isaac-google, isaac-gmail, isaac-mcp, isaac-claude-code | **red** — Commit not found | foundation `ad0a97b` |
| isaac-gchat | **red** — Commit not found | isaac-google at a bean-branch sha (find it in deps.edn; pin google main) + foundation `ad0a97b` |
| isaac-http (checkout isaac-server) | **red** | foundation `3963266` + agent `b6284e4` (the legs above) |
| isaac-hail, isaac-acp, isaac-cron, isaac-discord, isaac-hooks | green/other | foundation `ad0a97b` — green only via runner cache; goes red the day the branch is deleted |
| isaac-agent | — | foundation `1c8e45b` via leftover `bean/isaac-t1om` (leg 2 above) |

**Do**: in every repo above, repin foundation (and foundation-spec / test-support / marigold.*) to foundation main `0b120cc` or newer, gchat's google pin to google main, server's agent pin to agent main, agent's foundation pin to main. One commit per repo, `bb ci` green (or the pre-existing real reds noted — discord has 3 genuine test failures and hooks 1 that are NOT pin problems; leave those to their own beans unless the repin fixes them). Do NOT delete `bean/isaac-1fwl` or `bean/isaac-t1om` until every repo is repinned and green.

Order: foundation-dependent repos first (they only need the foundation repin), then gchat (needs google repinned first), then server. Land each via verify as it goes green — do not hold the fleet for the slowest one.
