---
# isaac-2yh9
title: isaac-agent main pins a foundation commit reachable from no branch
status: scrapped
type: bug
priority: high
created_at: 2026-09-15T23:45:52Z
updated_at: 2026-09-18T04:59:14Z
---

## Problem

isaac-agent `main` (commit `3f9fdc2`, "isaac-t1om: Retire :role directory-token alias of :cwd") pins isaac-foundation `1c8e45b5fdbbebfe5ff671c31fd1a138bbb8b434` throughout `bb.edn` (foundation, foundation-spec, test-support, marigold.*). That commit is on **no foundation branch**: foundation `main` is at `e6649143ac842b75c1640b4058379f1f46af9933`, and `git branch -r --contains 1c8e45b5` in a full mirror returns nothing.

Symptom on a machine whose gitlib cache lacks the object:

```
Error building classpath. Commit not found for marigold.longwave/marigold.longwave
  in repo https://github.com/slagyr/isaac-foundation.git at 1c8e45b5fdbbebfe5ff671c31fd1a138bbb8b434
```

`bb features`, `bb spec`, and `bb ci` all die before running. Found 2026-09-15 while bumping the gherclj pin; the same clone ran green earlier in the day at `3e3ef7e`, before `3f9fdc2` landed.

GitHub still serves the object by explicit sha, so it is recoverable but fragile: `fetch --all` does NOT retrieve it (it is unreachable from refs), and an unreferenced commit can be garbage-collected.

Workaround used locally:

```bash
git -C ~/.gitlibs/_repos/https/github.com/slagyr/isaac-foundation \
    fetch origin 1c8e45b5fdbbebfe5ff671c31fd1a138bbb8b434
```

After that, isaac-agent main builds and `bb features` passes (765 examples, 0 failures).

## Proposal

- Find out whether the foundation work behind `1c8e45b5` was pushed to a branch at all; if it exists elsewhere, push it (or merge it) so the sha is reachable.
- If the commit is obsolete, re-pin isaac-agent's `bb.edn` to a reachable foundation sha.
- Consider a guard in the module gate: refuse a pin whose sha is not reachable from a remote branch (`git branch -r --contains`), so an unpushed commit cannot ride into a release pin. Same class as the 2026-09-11 lesson about never fabricating a registry sha.

## Acceptance

- isaac-agent main builds from a cold gitlib cache (no by-sha rescue fetch).
- Every foundation sha in `bb.edn`/`deps.edn` is reachable from a foundation branch.


## Scrapped (2026-09-18, planner)
Superseded: the repin is isaac-lsz2 (agent leg added), the guard is isaac-j4jr (`bb lint-pins`, verify repins before squash). Note: 1c8e45b is currently reachable only via the leftover `bean/isaac-t1om` branch — do not delete that branch until lsz2 lands.
