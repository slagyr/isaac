---
# isaac-4or2
title: "tools.deps-based module loading: :modules as coordinate map"
status: completed
type: task
priority: normal
created_at: 2026-05-05T19:11:59Z
updated_at: 2026-05-05T19:50:00Z
---

## Description

Why: modules need their own transitive dependencies (jars, clojars artifacts, git libs) to do real work. The current filesystem-only model (modules/<id>/src/, hardcoded in bb.edn :paths) doesn't accommodate that and blocks third-party module support entirely. Switch to tools.deps-style resolution — :modules in isaac.edn becomes a map of module-id to tools.deps coordinate, resolved at startup. Everything composes from there.

## Manifest filename

Module manifest filename is **isaac-manifest.edn** (not module.edn). Namespacing the filename avoids classpath collisions when a module's tools.deps artifact ships alongside other libraries that might use a generic 'manifest.edn'. (io/resource "isaac-manifest.edn") unambiguously finds Isaac's manifest.

## End-to-end model

isaac.edn shape changes from a vector to a coordinate map:

  {:modules {:isaac.comm.discord  {:local/root "modules/isaac.comm.discord"}
             :isaac.comm.telly    {:local/root "modules/isaac.comm.telly"}
             :some.thirdparty/mod {:git/url "https://github.com/foo/bar" :git/sha "abc"}
             :other.thing         {:mvn/version "0.3.1"}}}

Built-in modules use :local/root. Third-party modules use whatever coord makes sense. Same syntax everywhere; tools.deps handles SAT-solving and download.

## Boot flow

1. Read isaac.edn.
2. Validate root cfg minus :modules (existing pass).
3. Resolve :modules coordinates -> classpath additions.
   - bb context: babashka.deps/add-deps
   - clj context: clojure.tools.deps/create-basis + add-libs
   - Tiny shim in isaac.module.loader picks the right one.
4. For each declared module-id, load its isaac-manifest.edn from the resolved classpath via (io/resource "isaac-manifest.edn").
   - Convention: isaac-manifest.edn lives at the artifact's classpath root. Modules' deps.edn includes :paths ["src" "resources"] and ships resources/isaac-manifest.edn.
5. Validate each manifest -> build module index (existing cccs flow, but driven by resolved classpath instead of filesystem walk).
6. Compose extends fragments into cfg schema (existing flow).
7. Validate full cfg.
8. Lazy activation continues to require :entry-ns on first capability use (existing bnv0 flow).

## Migration

- Each existing module gets a deps.edn:
  - modules/isaac.comm.discord/deps.edn (declares cheshire, http-kit, anything else Discord uses)
  - modules/isaac.comm.telly/deps.edn (probably empty :deps {} for now)
- Each existing module.edn is renamed to isaac-manifest.edn AND moved to modules/<id>/resources/isaac-manifest.edn so it's classpath-loadable.
- isaac.edn migrates from {:modules [:foo :bar]} to {:modules {:foo {:local/root "modules/foo"} ...}} — mechanical change.
- bb.edn / deps.edn host paths drop the per-module src entries; modules pull themselves in via tools.deps.

## Subsumes / supersedes

- isaac-sj3m (dynamic classpath at activation): superseded — this work covers it.
- isaac-mx1d (third-party module support): unblocked by design — drop a coord into isaac.edn :modules and Isaac resolves it.
- isaac-cccs (filesystem discovery): functionally retired — discovery is now classpath-resource-driven, not filesystem-walk-driven. The validation logic carries over.

## Open design questions (settle in implementation)

- Network failures during startup: hard error with the offending coord, or degraded boot? Lean hard error.
- Caching: rely on ~/.m2 and ~/.gitlibs; warm via Maven/Git as usual.
- Reload semantics: what happens if isaac.edn :modules changes at runtime? Phase 1 is restart-required; hot-reload of module set is a future concern.

## Acceptance

- isaac.edn supports the new {:modules {<id> <coord>}} shape.
- All existing modules (Discord, telly) migrate cleanly to deps.edn + resources/isaac-manifest.edn layout.
- bb.edn / deps.edn drops per-module :paths entries.
- A new third-party module can be added by writing a coord into isaac.edn :modules — no bb.edn edits, no rebuild.
- All existing features pass: features/modules/*, features/comm/discord/*, features/lifecycle/reconciler.feature.

## Acceptance Criteria

isaac.edn :modules takes a coordinate map; tools.deps resolves at startup in both bb and clj; modules carry their own deps.edn + resources/isaac-manifest.edn; existing modules migrated; bb.edn drops per-module :paths; all features and specs pass; third-party modules drop in without build edits

