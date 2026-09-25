---
# isaac-zr75
title: modules_pins fixture must not share a poisoned gitlibs REL cache
status: in-progress
type: bug
priority: high
tags:
    - foundation
created_at: 2026-09-25T16:21:58Z
updated_at: 2026-09-25T17:54:35Z
---

## Why

Verifier on isaac-i5on (2026-09-25), informational — do not reopen isaac-i5on.

Foundation `bb ci` is red on origin/main `c1cb377` and on a clean detached worktree: spec 1285/0, features 229 examples, 4 failures in `features/cli/modules_pins.feature:51,75,95,111`. The cached checkout `~/.gitlibs/_repos/file/REL/fixture-agent` has a remote that points at a deleted worker checkout (`/Users/zane/agents/isaac/work-1/isaac-foundation-i5on/fixture-agent`). `git fetch` exits 128.

This is the same class as the rxun / j4jr notes: a file-deps fixture is cached under a shared `REL` path, then a later checkout's deleted fixture dir poisons every foundation run on the machine. `rm -rf` of that cache makes the scenarios pass, until the next bean checkout recreates the poison. Workers keep reporting it as "pre-existing, not this bean." It is pre-existing. It is also still broken.

## Change

The fixture must not share a gitlibs cache key with another checkout's fixture, and a deleted fixture dir must not leave a fetchable remote behind.

- A file-coordinate fixture used by `modules_pins.feature` gets a cache identity that includes the checkout (or is never installed into the shared `~/.gitlibs/_repos/file/REL/` slot).
- A stale remote (fetch exit 128, remote path missing) is discarded and the fixture is recloned from the local fixture dir. It is not a test failure.
- The four scenarios pass on a machine whose `~/.gitlibs/_repos/file/REL/fixture-agent` remote points at a deleted path, without a human `rm -rf`.

## Acceptance

- [ ] On a machine with the poisoned cache (remote path does not exist), `bb features features/cli/modules_pins.feature` is green without a manual cache delete.
- [ ] A second foundation checkout running the same feature does not poison the first.
- [ ] `bb ci` on isaac-foundation origin/main is green for these four scenarios. Other pre-existing failures, if any, are named and not absorbed.

Likely repo: isaac-foundation (`features/cli/modules_pins.feature` and the fixture/gitlibs setup it uses).

## Ungated

No scenarios yet. Draft for human review. Do not promote. Do not reopen isaac-i5on.


## Planner note (2026-09-25, prowl@isaac-plan) — reproduced again on isaac-dv7p

Verifier on isaac-dv7p, informational. Do not reopen isaac-dv7p or isaac-i5on. Both stay completed.

Same four failures, second clean reproduction:

- foundation landed `9dd4bff`: `bb ci` 229 features, 4 failures at `features/cli/modules_pins.feature:51,75,95,111`
- clean foundation `c1cb377`: `bb features features/cli/modules_pins.feature` — 6 examples, 4 failures
- cache remote still points at deleted `/Users/zane/agents/isaac/work-1/isaac-foundation-i5on/fixture-agent`; `git fetch` exits 128
- agent CI on dv7p was green (1797 specs, 883 features) and is unrelated

This draft already owns the follow-up. Stays `draft` for human review. No scenarios yet, so it is not dispatched. Do not `rm -rf` the cache as the fix — that clears one poison and the next checkout recreates it.



## Design (planner review, 2026-09-25)

Two layers, both in isaac-foundation:

1. **Tests never touch the shared cache.** The feature harness sets tools.gitlibs's cache directory (`clojure.gitlibs.dir` system property, or `GITLIBS` env — tools.gitlibs honours both) to `<checkout>/target/gitlibs` before the first gitlibs call, so every checkout has its own cache and a deleted checkout can poison nothing. `isaac.module.coords/gitlibs-root` (hard-coded `~/.gitlibs/libs`) reads the same override so discovery and pins agree.
2. **Production self-heals a stale remote.** `isaac.modules.pins` resolves a relative `:git/url` to an absolute path against the deps directory before handing it to gitlibs (the cache key becomes that path, not the bare word), and when gitlibs fails because the cached repo's remote path no longer exists (fetch exit 128 / "does not exist"), it removes that `_repos` entry and retries once, logging `:modules.pins/cache-recloned` at info. A second failure is the real error.

## Acceptance (features/cli/modules_pins.feature — baselined)

- [ ] Scenario "the fixture is cached under this checkout, never in the shared gitlibs (isaac-zr75)". New step: `the gitlibs cache for "<url>" lives under this checkout's "target" directory` (asserts tools.gitlibs's effective cache dir is under the checkout's target/ and holds a repo for that url).
- [ ] Scenario "a cached fixture whose remote path no longer exists is recloned, not a failure (isaac-zr75)". New step: `the gitlibs cache holds "<url>" with a remote that points at a deleted path` (seeds the per-run cache with a clone whose origin is a nonexistent path).
- [ ] Existing four modules_pins scenarios green on a machine whose ~/.gitlibs slot is poisoned (planner verifies on zanebot after landing — one-time).
- [ ] Version bump; bb spec / bb features / bb lint green.

Do not reopen isaac-i5on or isaac-dv7p.

feature-baseline: isaac-foundation 066316f9d07b49cb4437e149069b18f7e0bdfdd6
feature-blob: isaac-foundation features/cli/modules_pins.feature 2131fadde42c59811eca025c7a06d665b184cfa9 137,147


## Worker checkpoint (2026-09-25)
Done: isolated bb feature gitlibs cache, resolved local URLs, retried stale remotes, implemented both baselined steps; focused modules_pins feature 8/0 and pins/coords specs green. Pushed foundation bean branch 2f14a3a.
Next: strengthen retry/log specs at spec/isaac/modules/pins_spec.clj:20, run bb spec, bb features, bb lint, bb ci; gate, rebase and land.


## Landed on main (2026-09-25)

main-sha: isaac-foundation 3a199d92c4f1ba73600e879f3a68559e2371980c

Verification: bb ci 1293 specs, 231 features (2 pre-existing pending berth-registration scenarios); bb lint 0 errors; gate PASS. Shared ~/.gitlibs/REL poison left untouched.
