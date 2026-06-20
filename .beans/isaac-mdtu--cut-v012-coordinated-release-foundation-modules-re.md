---
# isaac-mdtu
title: Cut v0.1.2 coordinated release (foundation + modules + registry + formula)
status: completed
type: task
priority: normal
created_at: 2026-06-19T16:41:52Z
updated_at: 2026-06-20T16:12:05Z
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

## Verification Notes (round 1 — manifest gap)

2026-06-19 verifier found manifests still at `0.1.0` while release tags were
correct; `modules list --edn` reported wrong `:version` after install.

## Manifest-version fix

Bumped `:version` in each module manifest to match its release tag; re-tagged
(immutable prior tags unchanged). Registry SHAs:

| module | manifest | tag | sha |
|--------|----------|-----|-----|
| agent | 0.1.3 | v0.1.4 | 632d7fe |
| server | 0.1.4 | v0.1.5 | 817a524 |
| acp | 0.1.3 | v0.1.4 | f8e1499 |
| cron | 0.1.2 | v0.1.3 | bf8208a |
| hail | 0.1.2 | v0.1.3 | 4d06719 |
| hooks | 0.1.2 | v0.1.3 | 7e9673b |
| discord | 0.1.2 | v0.1.3 | 6b8ae03 |
| imessage | 0.1.2 | v0.1.3 | aea6a8b |

Local proof: `modules install isaac.server isaac.cron` → list shows `0.1.4` /
`0.1.2`.

## Verification Notes (round 2 — green)

2026-06-19 verifier re-check:

- Published retagged modules match the handoff exactly: `agent v0.1.4 -> 632d7fe`, `server v0.1.5 -> 817a524`, `acp v0.1.4 -> f8e1499`, `cron v0.1.3 -> bf8208a`, `hail v0.1.3 -> 4d06719`, `hooks v0.1.3 -> 7e9673b`, `discord v0.1.3 -> 6b8ae03`, `imessage v0.1.3 -> aea6a8b`. [modules.edn](/Users/micahmartin/agents/verify/isaac/modules.edn:1) matches those SHAs.
- Tap is still correct: [Formula/isaac.rb](/Users/micahmartin/agents/work-2/homebrew-tap/Formula/isaac.rb:1) still points at foundation `v0.1.2`.
- Released manifests now carry the corrected top-level versions in the published artifacts. Spot checks: `git -C /Users/micahmartin/agents/verify/isaac-agent show FETCH_HEAD:resources/isaac-manifest.edn` for `isaac-agent v0.1.4` reports `:version "0.1.3"`, [isaac.server](/Users/micahmartin/.gitlibs/libs/isaac.server/isaac.server/817a5242b3c85bdcadbc4225c5d75f8fafc64c18/resources/isaac-manifest.edn:2) reports `0.1.4`, and [isaac.cron](/Users/micahmartin/.gitlibs/libs/isaac.cron/isaac.cron/bf8208a9370fc2c497c494eda5aa06713532189b/resources/isaac-manifest.edn:2) reports `0.1.2`.
- Clean proof is green on released foundation `v0.1.2`: `./libexec/isaac --root /private/tmp/isaac-mdtu-proof-root-2 --version` returned `isaac 0.1.2 (305c337)`, `init` succeeded, `modules install isaac.server isaac.cron` succeeded from the live registry, `modules list --edn` reported `:version "0.1.4"` for `isaac.server` and `:version "0.1.2"` for `isaac.cron`, and rerunning `--version` after install still returned `isaac 0.1.2 (305c337)`.


## Release-step clarification (2026-06-20): module tags are OPTIONAL

Because the registry (modules.edn) is SHA-ONLY (5h15), `modules install`/`upgrade`
resolve modules BY SHA — nothing consumes a module's git tag. So the REQUIRED
steps to release a MODULE (agent, acp, server, comms, cron, hail, hooks) are just:
  1. bump the module's manifest :version  (shows in `modules list`)
  2. bump the registry modules.edn :git/sha to the new commit
A git tag on the module is OPTIONAL — a human-readable "this commit = vX.Y.Z"
record / future-proofing if the registry ever goes tag-based. Tag for deliberate
releases if you want the marker; skip for quick fixes.

FOUNDATION is the exception — it MUST be tagged: the homebrew formula's url points
at .../archive/refs/tags/vX.Y.Z.tar.gz, so no tag => no installable tarball. (Plus
foundation manifest :version bump + the release.yml/formula auto-bump.)
