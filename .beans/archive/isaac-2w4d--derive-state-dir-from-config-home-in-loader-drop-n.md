---
# isaac-2w4d
title: Derive state-dir from config home in loader; drop nexus :state-dir slot
status: completed
type: task
priority: normal
created_at: 2026-05-24T23:10:14Z
updated_at: 2026-05-24T23:34:31Z
---

state-dir is currently passed in via opts, registered as its own nexus slot (app.clj:211), and loader/state-dir falls back to that slot. But loading config from home already determines state-dir = <home>/.isaac (parent of paths/config-root). Derive it in the loader, hang it on the config map (not user-settable), and drop the separate nexus slot + fallback. Part of a larger effort to unify config loading + Nexus population behind a single coordinator function (plan to follow).

## Summary of Changes

- `paths/state-dir` added (`<home>/.isaac`); `config-root` now derives from it.
- Loader `load-config-result` stamps `:state-dir` onto every returned config map (derived from `home`), so loading config inherently determines state-dir. Not user-settable.
- Removed the dedicated production `nexus/register! [:state-dir]` writes in `main`, `server/app`, and `session/cli`.
- `config/state-dir` now resolves: nexus slot (test injection via -with-nested-nexus) → config snapshot. Production never installs the nexus slot, so config is authoritative there.
- `hooks/handler` and `drive/turn` read state-dir from config/nexus instead of the removed runtime slot.
- Fixed latent `:stateDir` (camelCase) typo in session/cli resolve-state-dir.

## Known wrinkle (for the planned refactor)

Test harnesses (e.g. session_steps in-memory-state) model state-dir inconsistently with production: they pass the test dir as `:home` (config lands at `<dir>/.isaac/config`) but register a *flat* state-dir (`<dir>`, no `.isaac`) on the nexus. To keep both layouts green, the runtime reads prefer an explicitly-installed nexus `:state-dir` over the (production-correct) config value. The bigger config-loading unification should align the harness so config is unambiguously authoritative.

All green: 1697 specs, 658 features, 0 failures.
