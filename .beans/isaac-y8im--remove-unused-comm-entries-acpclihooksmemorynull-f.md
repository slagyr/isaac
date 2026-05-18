---
# isaac-y8im
title: remove unused :comm entries (acp/cli/hooks/memory/null) from core manifest
status: completed
type: task
priority: normal
created_at: 2026-05-18T22:31:47Z
updated_at: 2026-05-18T22:38:19Z
---

## Problem

`src/isaac-manifest.edn` declares `:comm {:acp :cli :hooks :memory :null}` with factory pointers. None of these factories are ever called via the comm dispatch path:

- `:acp` is wired by the manifest's `:route [:get "/acp"]` (WebSocket handler).
- `:hooks` is wired by `:route [:post "/hooks/*"]`.
- `:cli` is invoked by the CLI entry point directly.
- `:memory` and `:null` have no call sites in src/ (verified via grep on `isaac.comm.*/make` and `comm-registry/factory-for`; only configurator looks them up, and the configurator only fires on user `:comms` slots).

The module loader registers each factory via `isaac.api/register-comm!` on core activation, but no user can meaningfully set `:type :acp` etc. in their `:comms` config — these aren't user-configurable surfaces.

## Fix

- Delete the `:comm` slot from `src/isaac-manifest.edn` entirely. The route handlers stay (they wire ACP and hooks). CLI entry point unchanged.
- Drop the dead `static-comm-impls #{}` set in `src/isaac/config/loader.clj:796` and the branch in `check-comms` that consults it (loader.clj:840).
- Confirm `comm-kinds` (module/loader.clj:259) now returns an empty list when only the core manifest is in scope — that's correct: no user-configurable comm types ship out of the box.
- Survey `:configurable?` flag — likely unused after this and can be removed too.

## Out of scope

- `:memory` and `:null` factories: if any test or spec helper imports them directly (as a namespace, not via the registry), those imports stay. This bean only removes the manifest declaration.
- `:acp`, `:hooks`, `:cli` namespaces and their non-comm wiring — unchanged.

## Acceptance

- [ ] `:comm` slot removed from `src/isaac-manifest.edn`.
- [ ] `static-comm-impls` and its branch removed from `loader.clj`.
- [ ] Full test suite green: `bb spec && bb features`.
- [ ] ACP, hooks, and CLI continue to work end-to-end (route-served and CLI-invoked paths).

## Related

- isaac-vyz5, isaac-4cao, isaac-fw20 — independent of this; manifests can be cleaned up at any time.


## Summary of Changes

- `src/isaac-manifest.edn`: dropped the entire `:comm` slot (acp/cli/hooks/memory/null entries). Routes (`/acp`, `/hooks/*`) remain — they're separate from comm-registry.
- `src/isaac/config/loader.clj`: removed the dead `static-comm-impls #{}` def and the `static?` branch in `check-comms`.
- `spec/isaac/comm/acp_spec.clj`: removed the now-stale "comm-registered? for acp" assertion; the test now only asserts the `/acp` route registers on core activation.
- `features/modules/comm_extension.feature`: scenario "Multiple comm instances of the same :type coexist" switched from `:type :null` to `:type :telly` (with `:modules` declared) since `:null` is no longer in the core manifest.
- `features/cli/config.feature`: scenario "validate reports unknown comm type refs" declares telly and asserts the valid set contains `telly` (was previously asserting `acp.*cli.*memory.*null`).

`:configurable?` flag was already on the entries that got removed; no remaining manifest sets it, but `module-loader/comm-kinds` still filters by it harmlessly. Left in place — orthogonal cleanup.
