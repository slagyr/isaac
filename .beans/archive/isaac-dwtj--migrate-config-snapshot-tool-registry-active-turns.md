---
# isaac-dwtj
title: "Migrate config-snapshot, tool-registry, active-turns into isaac.system"
status: completed
type: task
priority: normal
created_at: 2026-05-08T20:31:11Z
updated_at: 2026-05-08T23:36:12Z
---

## Description

First migration tranche after isaac.system foundation lands. Three small singleton atoms move into isaac.system, eliminating per-namespace defonce atoms in their current homes.

| Concern | Today | After |
|---|---|---|
| Config snapshot | `config.loader/*config-snapshot*` (defonce atom) | `(system/get :config)` |
| Tool registry | `tool.registry/registry` (defonce atom) | `(system/get :tool-registry)` |
| Active turns | `bridge.cancellation/active-turns` (defonce atom) | `(system/get :active-turns)` |

Each migration follows the same shape: the namespace's API stays unchanged from the *outside*, but internally the defonce atom relocates. Startup wiring constructs the atom and calls `(system/register! :foo atom)`. The namespace's reader/writer fns dereference `(system/get :foo)` instead of the local atom.

External callers don't change — they still call `tool-registry/register!`, `config/snapshot`, `bridge/cancelled?` etc. Only the indirection layer underneath shifts.

## Tasks

### 1. Migrate config-snapshot
- Move construction out of `config.loader` defonce; into the startup wiring.
- `config.loader/snapshot` reads from `(system/get :config)`.
- `config.loader/set-snapshot!` writes via the system-registered atom.
- ~3 callers in config.loader internal API stay unchanged.

### 2. Migrate tool-registry
- Move `tool.registry/registry` defonce into startup wiring.
- All public fns in tool.registry (`register!`, `unregister!`, `lookup`, `all-tools`, `execute`, `tool-fn`, `tool-definitions`) read/write through `(system/get :tool-registry)`.
- ~10 callers across tool.builtin, tool.memory, drive.turn — no changes needed because they go through tool.registry's API.

### 3. Migrate active-turns
- Move `bridge.cancellation/active-turns` defonce into startup wiring.
- All public fns in bridge.cancellation (`begin-turn!`, `end-turn!`, `cancel!`, `cancelled?`, `on-cancel!`, `cancelled-result`, `cancelled-response?`) read/write through `(system/get :active-turns)`.
- ~6 external callers in drive.turn, llm.http, llm.api.grover, comm.acp.server — no changes (they go through bridge.cancellation's API).

### 4. Startup wiring

Add a `system/init!` (or similar) called during Isaac startup that constructs the three atoms and registers them. Order: config first (other code may read it during init); registries second; active-turns third.

Test setup parallels: each spec that touches these atoms uses `(system/with-system {:config ... :tool-registry ... :active-turns ...} ...)`.

## Why no new Gherkin scenarios

Pure refactor. External APIs unchanged from caller perspective. Existing feature/spec scenarios serve as regression tests.

## Out of scope

- Migrating other registries (slash, comm, provider) — those land with their respective extension-point beads.
- State-dir migration (separate bead — large scope).
- Module-index / module-loader atoms — those land in a future bead if needed.

## Acceptance Criteria

bb spec green; bb features green; no defonce atom remains in config.loader, tool.registry, or bridge.cancellation; the three atoms are constructed and registered into isaac.system at startup; tool-registry, config-snapshot, and active-turns are accessible via (system/get :tool-registry), (system/get :config), (system/get :active-turns); external APIs of those three namespaces unchanged (callers don't update).

