---
# isaac-wnyz
title: 'modules list: implied (transitive) modules show empty COORD {}'
status: in-progress
type: bug
priority: normal
tags:
    - unverified
    - in-progress
created_at: 2026-06-19T19:22:00Z
updated_at: 2026-06-19T19:24:38Z
---

On a real (git-coord) install the resolved tree shows transitive modules with
COORD `{}` instead of their resolved coordinate. Observed on zanebot v0.1.3:
  isaac.agent  0.1.0  ok  {}  isaac.comm.acp +3

## Root cause

list-configured-modules (src/isaac/module/loader.clj): EXPLICIT rows echo coord
from config — `(map? coord) (assoc :coord coord)`. IMPLIED rows take coord from
the discovery index entry — `(:coord entry)` — but a module discovered via the
classpath/manifest scan has `:coord {}` (discover-one / manifest-resource record
the manifest, not the resolving coordinate). So every transitive module renders
`{}`.

## Fix

The resolving coord IS available: transitive-module-requirements walks the
PARENT's deps.edn :deps, which pins the implied module at a real coord (e.g.
{:git/url ".../isaac-agent.git" :git/sha "a8f5a52a..."}). Capture that edge coord
into the index entry so implied rows show it. Surface it in the table (COORD),
--edn (:coord), and modules show (isaac-7e60).

## Acceptance

• An implied module's COORD shows its resolved coord (git url@sha), not {}.
• Same in --edn and `modules show`.

## Relationships

• Follow-on to isaac-90df (git-coord transitive discovery surfaced the modules;
  this captures their coord). Feeds isaac-7e60 (show detail).

## Handoff notes (work-3)

• Root cause confirmed: `:builtin?` modules (e.g. `:isaac.agent`) land in
  `builtin-index` with `:coord {}` after classpath preload; merge skipped them
  because they were already in the index.
• Fix: `merge-resolved-classpath-modules` overlays `discover-implied-entry` for
  all implied ids (not only absent keys).
• Regression: `git_coord_tree.feature` asserts `modules.1.coord.git/url` for
  `:isaac.agent`.
