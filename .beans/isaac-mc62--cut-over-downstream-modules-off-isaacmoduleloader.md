---
# isaac-mc62
title: Cut over downstream modules off isaac.module.loader re-exports (post-1tce)
status: todo
type: task
priority: high
created_at: 2026-08-03T21:02:00Z
updated_at: 2026-08-03T21:02:00Z
---

Parent lineage: isaac-1tce (module.loader split).

## Goal

Re-point all downstream callers of the **former** `isaac.module.loader` public
surface onto the post-split owning namespaces
(`coords` / `classpath` / `discovery` / `berths` / `lifecycle` / `versions` /
thinner `loader`), publish SHAs, bump pins, then **delete** the temporary
public re-exports on `isaac.module.loader`.

## Why this exists

isaac-1tce splits `isaac.module.loader` by responsibility. Clean cutover with
zero re-exports cannot land while published agent/server/hail (and live
`~/.isaac` pins) still call `isaac.module.loader/{register-handler!,activate!,
builtin-index,…}` via gitlibs. Without forwards, compose NPEs (~35 feature
failures); with forwards, AC #4 fails.

1tce is allowed **temporary** public re-exports of the former public surface so
foundation `bb ci` stays green. Local sibling rewires are **not** sufficient —
gitlibs SHAs are what compose loads. This bean finishes the multi-module
cutover.

## Scope

- Re-point callers in **isaac-agent**, **isaac-server**, **isaac-hail**, and any
  other module still requiring the old loader path.
- Publish module SHAs and bump:
  - orchestration `modules.edn` pins
  - live `~/.isaac` (and CI) pins
- Delete the temporary public re-exports from `isaac.module.loader` in
  isaac-foundation.
- Foundation `bb ci` green with **zero** loader re-exports remaining; feature
  suite green under a real multi-module root.

## Acceptance

- [ ] No remaining external callers of the temporary loader re-export surface
      (grep across modules + live pin set).
- [ ] Temporary re-exports removed from `isaac.module.loader`.
- [ ] `bb ci` green in foundation and in each rewired module.
- [ ] Feature suite green against a compose path that loads the rewired SHAs
      (no NPEs from missing `loader/*`).
- [ ] isaac-1tce AC #4 (no re-export shims) fully satisfied end-to-end.

## Depends on

- isaac-1tce merged (foundation split + temporary re-exports + 1:1 specs in
  place).

## Notes

- Worker left uncommitted local agent/server rewires during 1tce for this path —
  reclaim or redo them here; do not leave them half-applied on main checkouts.
