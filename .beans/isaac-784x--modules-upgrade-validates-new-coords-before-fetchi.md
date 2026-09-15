---
# isaac-784x
title: 'modules upgrade refuses a valid upgrade in the live root: staged validation reports comm type errors the pre-change load does not'
status: draft
type: bug
priority: high
tags:
    - foundation
    - modules
    - deploy
created_at: 2026-09-15T18:31:56Z
updated_at: 2026-09-15T18:31:56Z
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
