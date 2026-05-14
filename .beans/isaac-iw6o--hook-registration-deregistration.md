---
# isaac-iw6o
title: Hook registration / deregistration
status: todo
type: feature
priority: normal
created_at: 2026-05-14T14:39:17Z
updated_at: 2026-05-14T16:14:26Z
---

Promote hooks from a hardwired prefix entry in `src/isaac/server/routes.clj` into a **built-in module** (in-source, structurally a module like `isaac.comm.acp`). The hooks module owns `/hooks/`, owns the per-name registry, and exposes a registration API. This opens — but does not yet exercise — a path for other modules to declare hooks.

## Scope (Path 1, Scope B)

- Refactor only. No new production hook is added. No fixture module is added.
- Existing dispatch contract (`features/server/hooks.feature`, 8 existing scenarios) must continue to pass unchanged.
- One new scenario at `features/server/hooks.feature:92` (`@wip`) covers config removal → 404.

## Architecture

- New built-in module `isaac.server.hooks` (rename or co-locate; keep in `src/`). Manifest entry in `src/isaac-manifest.edn` parallels the ACP comm entry: `:isaac/factory` + `:bootstrap`.
- The hooks module's bootstrap calls `routes/register-route! :post "/hooks/" 'isaac.server.hooks/handler {:with-opts? true}` (or the existing prefix shape). Remove the hardwired entry in `src/isaac/server/routes.clj:35-37`.
- Module exposes `register-hook!` / `deregister-hook!` and an internal name → handler registry (atom).
- New `:hook` extension kind added to `known-extend-kinds` in `src/isaac/module/manifest.clj:28`. Modules may declare `:extends {:hook {<name> {:isaac/factory ...}}}`. Activation registers; deactivation (if/when supported) deregisters. **No module currently uses this** — it's API only.
- Config-driven hooks: a `Reconfigurable` impl on the hooks module reconciles the `:hooks` config slice against the registry on every config change. Add → registered. Remove → deregistered. No restart required.
- **Collision rule:** if a module-declared hook name matches a config-declared hook name, startup fails with an error containing `hook name collision: <name>`. Logged as `:hook/collision`. (See "Why error, not override" below.)
- In-flight POSTs are not the registry's concern. `dispatch-turn!` hands the turn to the bridge; removing the registry entry can't unwind a turn already running. No drain machinery needed.

## New log event vocabulary

- `:hook/registered` — name added to the registry (source: `:config` or `:module`)
- `:hook/deregistered` — name removed (source + reason)
- `:hook/dispatched` — POST resolved a hook and called `dispatch-turn!`
- `:hook/collision` — startup-time collision between config and module sources

## New gherclj step phrases (steps to add)

- `the isaac file "<path>" is removed` — inverse of the existing `... exists with:` step. General-purpose; useful well beyond this bean.
- `the isaac config is reloaded` — explicit trigger so scenarios don't race the filesystem watcher. **Note:** once this step exists, several existing scenarios that currently rely on implicit/auto-reload triggers should be simplified to use it. Out of scope for this bean — flag as a follow-up sweep.

## Why error on collision (not override)

A module's hook handler is real code with its own contract. A config-declared hook is a YAML template + session-key. Silently letting one shadow the other invites a typo (`heartrate` in config, `heartrate` in a future telemetry module) to silently disable the module's handler. Errors fail loud, which is what a registry-collision should be.

## Acceptance

- [ ] Existing 8 scenarios in `features/server/hooks.feature` pass unchanged.
- [ ] `@wip` removed from the new scenario at `features/server/hooks.feature:92`.
- [ ] New scenario passes: `bb features` (after `@wip` removal, the scenario runs by default).
- [ ] Hardwired `:uri-prefix "/hooks/"` entry removed from `src/isaac/server/routes.clj`.
- [ ] New built-in module entry present in `src/isaac-manifest.edn` for hooks.
- [ ] `:hook` listed in `known-extend-kinds` in `src/isaac/module/manifest.clj`.
- [ ] Unit specs cover the registry contract: `register-hook!` adds, lookup finds, collision throws. No HTTP needed — fast, in-process.
- [ ] New steps implemented: `the isaac file "<path>" is removed`, `the isaac config is reloaded`.
- [ ] Run command: `bb features features/server/hooks.feature` (full file) and the unit specs for the registry.

## Out of scope (deferred)

- Module-declared hooks E2E scenario. No production module needs one. Add when the first real caller appears.
- Sweeping existing scenarios to use the new `the isaac config is reloaded` step in place of implicit triggers. Follow-up.
- Hook deactivation on module removal — currently no module declares a hook, so this path isn't exercised. The API supports it; testing it waits for a real caller.

## Prior art

- `isaac-xibj` (completed): Discord plugin lifecycle — Reconfigurable + diff-reconcile. Same shape; reuse it.
- `isaac-yonq` (completed): built-in surface manifest with `:isaac/factory`. Hooks module slots in here.
- `isaac-up9y` (completed): slash command extension point. `:slash-command` is the precedent for `:hook` extension kind.
