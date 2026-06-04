---
# isaac-qpgy
title: :cli re-declared as a berth (phase 4 of berth epic)
status: todo
type: feature
priority: normal
created_at: 2026-06-04T14:43:34Z
updated_at: 2026-06-04T14:43:48Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
    - isaac-yb39
    - isaac-f77b
    - isaac-c2g5
    - isaac-8yxs
---

Phase 4 of isaac-brth: prove the loop end-to-end by hosting isaac's
own built-in CLI commands through the public berth mechanism. After
this lands, the foundation isn't privileged — it uses the same
extension API that third-party modules use.

## Two halves

**4a — declare `:cli` as a berth in core.**

- Add a `:berths {:cli {…}}` declaration to isaac's foundation
  manifest. Shape: manifest-only berth, entry-level `:factory`
  invoked per CLI contribution to register the handler in the
  command registry.
- Rewire `isaac.module.loader` to read `:cli` through the berth
  declaration rather than via the hardcoded `known-extend-kinds`
  set.
- Rewire `isaac.main` so it no longer statically requires built-in
  command namespaces. The berth's contributions list (drawn from
  manifests) becomes the source of truth.
- CLI dispatch precedes server boot. The foundation processes the
  `:cli` berth BEFORE running Module/on-startup hooks, since CLI
  handlers are stateless registrations that don't need lifecycle.

**4b — convert one built-in command to a manifest contribution.**

- Pick `init` (no module deps; pure setup logic).
- Move its registration from a static-require + side-effect
  `register!` into the foundation manifest's `:cli` contribution map.
- Existing `init` behavior and tests unchanged.

## Fixture

Same `spec/marigold/bridge` + `spec/marigold/longwave` modules from
isaac-jr64 / isaac-8yxs, extended:

- `spec/marigold/bridge/resources/isaac-manifest.edn` declares
  `:marigold.bridge/cli` as a manifest-only berth (parallel shape
  to core's `:cli`). Lets the scenario test the berth machinery
  without coupling to isaac-core's manifest.
- `spec/marigold/longwave/resources/isaac-manifest.edn` contributes
  a CLI command `:longwave-ping` whose handler prints `"pong"`.

## Feature

`features/module/cli_as_berth.feature` — one `@wip` scenario:

- Running `isaac longwave-ping` dispatches through the berth
  mechanism and prints `pong`.

## Acceptance

- Remove `@wip` from `features/module/cli_as_berth.feature`.
- `bb features features/module/cli_as_berth.feature` passes.
- ALL existing CLI tests (init, sessions, chat, acp, version, help,
  …) still pass — no behavioral regression.
- `init` no longer appears in `isaac.main`'s static-require list;
  its registration flows through the foundation manifest.
- A grep for `isaac.module.loader/known-extend-kinds` reveals `:cli`
  is no longer hardcoded; the loader resolves it through the berth
  index.
- A worker can demonstrate the symmetry by adding a second built-in
  conversion (or by demonstrating a third-party module can ship a
  CLI command with no isaac-core changes — the scenario above is
  exactly this).

## Out of scope

- Converting every built-in command. One conversion (init) proves
  the loop; the rest can land incrementally in follow-up beans.
- CLI completion / dynamic help generation. Future bean if useful.
- Hot-reload of CLI berth contributions. Foundation only processes
  the berth at startup.
- Foundation/platform split (phase 9 of brth). Distinct bean later,
  AFTER 4–8 ship.

## Dependencies

- isaac-htkp (manifest shape)
- isaac-yb39 (contribution validation — `:cli` contributions
  validated against the berth's `:manifest :schema`)
- isaac-f77b (Module protocol — modules load to expose their
  handler symbols)
- isaac-c2g5 (lexicon — `:type :symbol` in the berth's manifest
  schema)
- isaac-8yxs (manifest-only berth processing — `:cli` is structurally
  identical to `:route`)

## Notes for the worker

- CLI dispatch happens BEFORE deps resolution for most invocations
  — `isaac init` works on a machine with nothing installed. So the
  `:cli` berth has a special bootstrap window: read the foundation
  manifest, register its `:cli` contributions, dispatch. Other
  modules' `:cli` contributions land only once those modules are
  loaded (via user `:modules` declaration).
- The foundation manifest probably lives at a known resource path
  (e.g. `resources/isaac-manifest.edn` in isaac core's classpath).
  The loader reads it at boot.
- Be careful with `isaac.main`'s start sequence. Today it does:
  parse args → require built-in command namespaces → dispatch. After
  4a/4b it should be: parse args → load foundation manifest →
  register `:cli` berth contributions → dispatch.
- Touch `isaac.main/register-module-cli-commands!` too — it has its
  own discovery pass that pre-dates berths. Decide if it still
  earns its keep or if the berth pass subsumes it.
