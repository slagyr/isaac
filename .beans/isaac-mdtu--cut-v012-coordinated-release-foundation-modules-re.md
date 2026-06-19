---
# isaac-mdtu
title: Cut v0.1.2 coordinated release (foundation + modules + registry + formula)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-19T16:41:52Z
updated_at: 2026-06-19T17:29:38Z
blocked_by:
    - isaac-xc9n
---

Unblocked: isaac-92p3 (seed-authoritative foundation) is DONE. All the recent
work (92p3, 0yp1, iy94, modules-list tree, multi-install) lives on foundation
main and the module repos — but NOTHING is released, so zanebot / any brew user
is stuck on v0.1.1 (actually runs v0.1.0 foundation via module pins). Cut v0.1.2.

## 92p3 simplifies this

Because the seed foundation now wins over any module's transitive foundation pin
(:exclusions #{seed-foundation-lib}), bumping every module's foundation pin is no
longer a CORRECTNESS requirement (runtime uses the installed seed regardless).
Still bump them for dev/test CONSISTENCY (modules should build/test against the
release foundation), but the lockstep-or-it-breaks pain is gone.

## Checklist

1. Confirm foundation main has the intended set: 92p3, 0yp1, iy94, modules-list
   tree, multi/all-install (its own bean). Bump foundation manifest :version to
   0.1.2.
2. Tag foundation v0.1.2 — IMMUTABLE; never move it (7tle / 5h15 lesson).
3. Each module repo (agent, server, acp, cron, hail, hooks, discord, imessage),
   in dependency order (agent before its dependents): bump deps.edn foundation
   pin (+ inter-module pins) to v0.1.2 for consistency; commit; tag v0.1.2
   (immutable).
4. Bump registry modules.edn: each module's SHA-ONLY coord -> its v0.1.2 commit.
5. Bump formula isaac.rb -> foundation v0.1.2 (via the foundation-release
   dispatch from 7tle, or manual).
6. Verify on a CLEAN machine: brew install -> isaac --version = 0.1.2; modules
   install X works (multi-install too); and DOGFOOD 92p3 — install a module that
   pins an older foundation and confirm --version STAYS 0.1.2 (seed wins).
7. zanebot: brew upgrade isaac -> 0.1.2; modules list shows the new tree format.
   No module re-pin dance needed (thanks to 92p3).

## Relationships

• Unblocked by isaac-92p3 (done). Includes the multi/all-install bean.
• Uses 7tle's auto-bump (foundation-release dispatch). 5h15 = the consistency
  lesson that made this necessary.


## Handoff

Foundation **v0.1.2** tagged at `305c337` (manifest 0.1.2). GitHub Release published; tap auto-bump failed (missing `HOMEBREW_TAP_BUMP_TOKEN`) — formula bumped manually in homebrew-tap @ `5b8b81d`. Tap `brew test` CI green.

### Module release SHAs (registry @ isaac `390ecfe6`)
| module | tag | sha |
|--------|-----|-----|
| agent | v0.1.3 | 4abb96b |
| server | v0.1.4 | 2fb78f4 |
| acp | v0.1.3 | f488107 |
| cron | v0.1.2 | 4acf02e |
| hail | v0.1.2 | 9821901 |
| hooks | v0.1.2 | 63ec28b |
| discord | v0.1.2 | db8d01b (+ feature-bootstrap fix) |
| imessage | v0.1.2 | 8607c29 |

Note: agent/acp/server used next patch tags — v0.1.2 already taken immutably on remote.

### Extras bundled in agent/discord releases
- agent: cli-usage feature fix for trimmed `--root` help (7b5d7b4)
- discord: feature-bootstrap registry dedupe fix (private gherclj var + keep session Grover setup)

### Verify locally
`./libexec/isaac --version` → `isaac 0.1.2 (305c337)`
Remote formula: raw.githubusercontent.com/slagyr/homebrew-tap/main/Formula/isaac.rb → v0.1.2
