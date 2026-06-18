---
# isaac-0yp1
title: 'Module dependency resolution: ''modules install'' pulls required modules transitively'
status: draft
type: feature
created_at: 2026-06-18T19:21:29Z
updated_at: 2026-06-18T19:21:29Z
---

Today `isaac modules install <name>` installs ONLY the named module (registry
lookup -> write one coord). Real modules need others: e.g. installing
isaac.hail or a comm module without isaac.agent/isaac.server yields a half-wired
assistant. Users shouldn't have to know and hand-install the dependency graph.

## Goal

`modules install <name>` installs <name> AND its required modules (transitive
closure), writing them all into config :modules. "Compose the assistant" should
mean: name what you want, get what it needs.

## Design

1. REGISTRY declares deps. Add `:requires [<module-id> ...]` to modules.edn
   entries (isaac-xdg3 owns the file; this needs the entries populated — e.g.
   :isaac.hail :requires [:isaac.agent]; comm modules -> [:isaac.agent];
   :isaac.server -> [:isaac.agent]; verify each module's real needs).

2. INSTALL resolves the closure. On `install <name>`: walk :requires, dedupe,
   skip already-installed, write every new module's coord to :modules. Cycle-
   safe (visited set). Confirm with a summary: "Installed isaac.hail (+ deps:
   isaac.agent)".

3. Idempotent. Re-installing is a no-op for already-present modules; only new
   ones are added/announced.

## Open questions (decide in design)

• remove: should `remove <name>` also remove now-orphaned deps? Lean NO by
  default (a dep may be required by another installed module / be wanted
  standalone); maybe a `--prune` flag that removes deps not required by anything
  else. Decide.
• Version conflicts: if two modules require different coords/tags of the same
  dep, how to resolve? v1 could take the registry's canonical coord and warn.
• Should deps be recorded as explicitly-installed vs pulled-as-dependency
  (affects prune)? v1 can treat all :modules entries equally; revisit if prune
  lands.

## Acceptance (feature-test, features/module)

• Registry where :isaac.hail :requires [:isaac.agent]; `install isaac.hail` ->
  config :modules gains BOTH :isaac.hail and :isaac.agent; `modules list` shows
  both :ok; confirmation names the pulled deps.
• Re-running install is idempotent (no dupes, no re-announce).
• A cycle in :requires terminates (no infinite loop).
• An unknown id in :requires -> clear structured error; partial closure not
  half-written (all-or-nothing, or clearly reported).

## Relationships

• Builds on isaac-iy94 (install write-path correctness — dotted-id fix,
  multi-write, validation). Do iy94 first; dep-install writes MULTIPLE coords,
  so it needs the corrected whole-map write.
• Registry data: isaac-xdg3 (modules.edn) must carry :requires per module.
• Part of the dhzy `modules` command surface.
