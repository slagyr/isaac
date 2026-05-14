---
# isaac-ft8g
title: Decouple hooks from ACP; promote isaac.hooks to first-class namespace
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-05-14T19:26:47Z
updated_at: 2026-05-14T20:26:35Z
---

Cleanup of architectural fallout from `[[isaac-iw6o]]` (hooks-as-built-in-module). When hooks was promoted to a module, its bootstrap absorbed ACP's route registration — `isaac.server.hooks/register-all-routes!` calls `isaac.comm.acp/register-routes!` via `requiring-resolve`. Cross-module coupling, encoded in code rather than in the manifest. And the namespace path still reads "server detail."

## Scope

Five small, related cleanups:

1. **Rename `isaac.server.hooks` → `isaac.hooks`.** Hooks is now a first-class module peer to `isaac.comm`, `isaac.drive`, `isaac.bridge`. The `server/` location is leftover from before it was a module.

2. **ACP gets its own `:bootstrap` back.** Restore `:bootstrap isaac.comm.acp/register-routes!` (or split the manifest entries if v2 lands first — defer to whatever shape the manifest is in when this lands).

3. **Hooks bootstrap stops calling into ACP.** Delete `register-all-routes!` from `isaac.hooks`. Its `register-routes!` registers only `/hooks/`.

4. **Drop the redundant direct call in `app.clj:212`.** Currently `(hooks/register-routes!)` runs in `start!` AFTER the manifest bootstrap already ran it. Single source of truth: manifest bootstrap.

5. **Drop `:with-opts?` from route registration.** Handlers can pull `:state-dir`, `:cfg-fn`, and config snapshot from `isaac.system` / `isaac.config.loader` directly — they're globally available after server boot. Convert the hooks handler and the ACP websocket handler to take `(handler request)` only. The hooks handler's `(system/with-system {:state-dir state-dir} ...)` block goes away — state-dir is already in the system from boot.

## Acceptance

- [ ] `src/isaac/server/hooks.clj` moves to `src/isaac/hooks.clj`; `isaac.server.hooks` ns → `isaac.hooks`. All callers updated.
- [ ] `isaac.hooks` namespace has zero references to `isaac.comm.acp` (verifiable via grep; worth an assertion in a spec).
- [ ] `register-all-routes!` deleted from `isaac.hooks`.
- [ ] `app.clj:212` direct call to `hooks/register-routes!` deleted.
- [ ] Manifest's `:bootstrap` is the canonical registration trigger.
- [ ] ACP and hooks each have their own `:bootstrap` (or whatever the equivalent is in the manifest shape at land time).
- [ ] `:with-opts?` removed from `isaac.server.routes/register-route!` and `register-prefix-route!`. The `invoke-route` function always calls `(handler request)`.
- [ ] Hooks handler reads state-dir/config from `isaac.system` / `isaac.config.loader` directly.
- [ ] ACP websocket handler reads its needs from the same global accessors.
- [ ] All existing 8 scenarios in `features/server/hooks.feature` pass.
- [ ] All existing ACP comm scenarios pass.

## No new feature scenarios

This is a refactor — existing contract preserved. Coverage comes from existing feature suites continuing to pass.

## Unit tests

- Add a unit test asserting `isaac.hooks` has no dependency on `isaac.comm.acp` (read the ns form, check `:require` clauses). Catches future regressions of the same cross-coupling.
- Unit tests for the route handlers in their new global-accessor form: handler called with just a request, internals fetch state-dir/config from system.

## Out of scope

- Declarative `:route` extension kind in the manifest. Tracked separately as a v2-dependent bean.

## Related

- `[[isaac-iw6o]]` — predecessor; introduced the cross-coupling we're undoing here.
- `[[isaac-zl32]]` — manifest v2; this bean's manifest changes will need light coordination with whatever shape v2 lands in, but the cleanups themselves are independent.
