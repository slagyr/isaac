---
# isaac-a7c0
title: Cut over downstream callers off isaac.config.loader re-exports (post-flgy)
status: todo
type: task
priority: normal
created_at: 2026-08-03T21:01:59Z
updated_at: 2026-08-03T21:01:59Z
---

Parent lineage: isaac-flgy (config.loader split).

## Goal

Re-point all downstream callers of the **former** `isaac.config.loader` public
surface onto the post-split owning namespaces, then **delete** the temporary
public re-exports on `isaac.config.loader`.

## Why this exists

isaac-flgy splits `isaac.config.loader` by responsibility (env / parse /
companions / entities / normalize / warnings / thinner loader). Clean cutover
with zero re-exports cannot land while published agent (and other modules)
still call `loader/normalize-config`, `loader/env`, etc. via gitlibs.

flgy is allowed **temporary** public re-exports of the former public surface so
foundation `bb ci` stays green. This bean finishes the cutover.

## Scope

- Re-point callers in **isaac-agent** (and agent-spec), plus any other module
  that still requires `isaac.config.loader/{normalize-config,env,…}`.
- Bump published SHAs / pins as needed so compose paths load the rewired
  modules (local sibling rewires alone are not enough — gitlibs SHAs are what
  compose loads).
- Delete the temporary public re-exports from `isaac.config.loader` in
  isaac-foundation.
- Foundation `bb ci` green with **zero** loader re-exports remaining.

## Acceptance

- [ ] No remaining external callers of the temporary loader re-export surface
      (grep across modules + live pin set).
- [ ] Temporary re-exports removed from `isaac.config.loader`.
- [ ] `bb ci` green in foundation and in each rewired module.
- [ ] isaac-flgy AC #4 (no re-export shims) fully satisfied end-to-end.

## Depends on

- isaac-flgy merged (foundation split + temporary re-exports in place).
