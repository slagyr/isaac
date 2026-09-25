---
# isaac-zr75
title: modules_pins fixture must not share a poisoned gitlibs REL cache
status: draft
type: bug
priority: normal
tags:
    - foundation
created_at: 2026-09-25T16:21:58Z
updated_at: 2026-09-25T16:21:58Z
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
