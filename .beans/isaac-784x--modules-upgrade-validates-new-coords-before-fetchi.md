---
# isaac-784x
title: 'modules upgrade refuses a valid upgrade in the live root: staged validation reports comm type errors the pre-change load does not'
status: completed
type: bug
priority: high
tags:
    - foundation
    - modules
    - deploy
created_at: 2026-09-15T18:31:56Z
updated_at: 2026-09-15T18:55:49Z
---

## Problem

`isaac modules upgrade <id>` refused a valid upgrade on zanebot's live root, printing

```
error: comms[:discord] - unknown :type "discord"
error: comms[:imessage] - unknown :type "imessage"
```

and leaving `:modules` unchanged (2026-09-15, agent 0.1.68 `f6dd38d` → 0.1.69 `3e3ef7e`, twice).

Evidence:
- `isaac config validate` on the same live config reports no comm errors.
- Pre-fetching the new gitlib (`~/.gitlibs/libs/isaac.agent/isaac.agent/3e3ef7e…`) did NOT help — so it is not "validates before fetching" (this bean's first diagnosis was wrong).
- The same foundation (brew keg `82e3594`, which is also the agent's foundation pin) and the same config copied into a fresh root (`ISAAC_ROOT=~/isaac-rehearsal-0169a`) upgraded fine: `Upgraded isaac.agent: f6dd38d -> 3e3ef7e`, `modules list` 0.1.69 ok.
- `cli.log` records no warn/error for the failed attempts.

Mechanism (isaac-foundation `src/isaac/config/mutate.clj` `set-config`): the write is blocked only by errors that are *new* versus `current` (`loader/load-config-result {:root root}` on the real fs). `validate-plan` stages the config tree into a mem-fs and loads it there. The staged load reports the comm type errors in both roots; in a fresh root the pre-change load reports them too (so they count as pre-existing and the write proceeds), while in the live root the pre-change load does not (suspect: the live root's startup cache / warm module index), so they count as new and the write is refused. Not yet confirmed which part of the live root makes the difference.

Workaround used: run the upgrade in a fresh copy of the config root, diff the resulting `isaac.edn` against the live one (only `:isaac.agent` sha differed), copy it over, verify `isaac modules list`, restart.

## Proposal (not decided)

- Make the staged validation load modules the same way the pre-change load does (same fs view of module checkouts / cache), so comm types contributed by comm modules resolve in both.
- Add a regression: `modules upgrade` of a registry module in a root with a warm startup cache succeeds.

## Acceptance (draft — scenarios TBD)

- Upgrading a registry module on a root with a warm startup cache succeeds and prints `Upgraded <id>: <old> -> <new>`.
- A genuinely invalid config still blocks the upgrade with its real errors.

## Implementation (2026-09-15)

Root cause confirmed: config mutation compared the staged cold load against `loader/load-config-result` without bypassing the warm startup config snapshot. On a live root, cached validation errors could differ from the staged filesystem load and be misclassified as newly introduced.

Implemented `:skip-cache?` on `load-config-result` and use it for both `set-config` and `unset-config` pre-change baselines. Staged and current comparisons now both validate filesystem state directly while genuine new errors remain blocking.

Branch: `bean/isaac-784x @ 7c20ccf` (base `origin/main@c629ec4`). Verification: focused loader/mutate specs 36 examples, 0 failures; `features/module/modules_upgrade.feature` 2 examples, 0 failures; `ISAAC_GIT=1 bb ci` 1025 specs + 184 feature examples, 0 failures (2 pre-existing pending berth-observability scenarios). Initial CI feature run encountered a stale global gitlibs fixture remote referencing another worktree; removing only that generated cache entry produced the clean rerun.



## Landed on main (2026-09-15)

main-sha: isaac-foundation 25244a6b3c5690f1dafb5254c27aea0a96dc9477

Verify gate (perceptor@isaac-verify): loader/mutate specs 36/0/85; modules_upgrade.feature 2/0/7; bb spec 1025/0/1850. Full bb features hit 2 reds in cli/modules_pins.feature (stale gitlibs remote pointing at /Users/zane/agents/isaac/work-1/isaac-foundation-784x/fixture-agent); same 2 reds reproduce on origin/main c629ec4 — pre-existing machine cache, not this bean. No feature tampering. Real-root upgrade smoke deferred (no zanebot restart).
