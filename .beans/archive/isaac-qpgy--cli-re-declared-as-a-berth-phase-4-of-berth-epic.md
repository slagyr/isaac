---
# isaac-qpgy
title: :cli re-declared as a berth (phase 4 of berth epic)
status: completed
type: feature
priority: normal
created_at: 2026-06-04T14:43:34Z
updated_at: 2026-06-05T06:13:30Z
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

## Summary of Changes

### Phase 4a — `:cli` declared as a berth in core

- **`src/isaac-manifest.edn`**: `:berths {:cli {:description …  :manifest {:schema {:type :seq :spec {:type :map :factory isaac.cli/register-cli-command! :schema {…}}}}}}`. The berth's entry-level factory wires each contribution into the registry.
- **`src/isaac/module/manifest.clj`**: removed `:cli` from `known-extend-kinds`, kept it as a known meta-key with `{:type :ignore}` so legacy manifests with top-level `:cli` still parse. Relaxed `collect-berth-errors` so isaac.core may declare un-namespaced foundational berths (`:cli`/`:route`/`:tools`); third-party modules still must namespace their berths.
- **`src/isaac/module/loader.clj`**: dropped `register-cli-extension!` and the `:cli` case from `register-extensions!`. CLI contributions now flow exclusively through `process-manifest-berths!`.
- **`src/isaac/main.clj`**: `register-module-cli-commands!` rewritten to (a) read user config opportunistically, (b) call `discover!` inside a nested-nexus wrap so it sees `mem-fs`, (c) exit the wrap before invoking `process-manifest-berths!` (so the berth's registry writes survive the wrap's `install! previous` restore), and (d) call `clear-berth-commands!` first so stale module contributions don't survive a re-run.

### Phase 4b — `init` as a manifest contribution

- **`src/isaac/cli.clj`**: removed the static `(register! {:name "init" …})` side effect. Added `register-cli-command!` (the berth's per-entry factory) which resolves symbol-valued `:run-fn`/`:help-text` and registers a command spec. The run-fn is wrapped with generic `--help` handling so module-supplied commands get a per-command help page the same way `register-module-command!` did. Added `clear-berth-commands!` that drops only commands previously installed by `register-cli-command!` (statically-registered commands are unaffected).
- **`src/isaac-manifest.edn`**: `:cli [{:name "init" :usage "init" :desc "…" :run-fn isaac.cli/init-run-fn :help-text isaac.cli/init-help}]`. The foundation manifest's :cli vector is the source of truth.

### Tests / fixtures

- **`features/module/cli_as_berth.feature`**: `@wip` removed. The longwave-ping scenario now passes.
- **`spec/marigold/bridge`**: declares its own `:marigold.bridge/cli` manifest-only berth + factory `marigold.bridge.cli/register-cli-command!` (mirror of core's, with a namespaced berth id so it exercises the htkp-validated path).
- **`spec/marigold/longwave`**: contributes `longwave-ping` to that berth via `marigold.longwave/longwave-ping-run-fn`.
- **`bb.edn`**: `spec/marigold/{bridge,longwave}/resources` added to `:paths` so the fixture manifests are discoverable on the classpath when main runs in-process.
- **`modules/isaac.cli.greeter`**: updated to the new `:cli [{…}]` seq shape (was the old `{:cli {<id> {:factory …}}}` map). Run-fn is the existing `isaac.cli.greeter/run-fn` (no factory layer needed — register-cli-command! does the work).
- **`spec/isaac/cli_spec.clj`** (CLI Init): the (around) block now calls `process-manifest-berths!` over `core-index` so the registry is seeded with init before each test (replaces the old auto-registration via namespace load).
- **`spec/isaac/main_spec.clj`**: "reads module cli config from an explicit fs" updated to mock the discover! result with both the berth declaration (on `:isaac.core`) and a contribution from `:hello`. The obsolete "clears stale module commands before re-discovery" test removed — clearing is now handled by `clear-berth-commands!`, which gets coverage via the integration path.
- **`spec/isaac/module/loader_spec.clj`** and **`spec/isaac/module/manifest_spec.clj`**: removed obsolete tests covering the activate-time `:cli` path and the strict `read-manifest`-level `:cli` validation (now delegated to the berth's manifest schema).

### Acceptance checks

- `bb spec`: 1853 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `grep :cli src/isaac/module/manifest.clj`: not in `known-extend-kinds`.
- `grep 'register!.*"init"' src/isaac/cli.clj`: none (static registration gone).
- `grep register-cli-extension src/isaac/module/loader.clj`: none.
