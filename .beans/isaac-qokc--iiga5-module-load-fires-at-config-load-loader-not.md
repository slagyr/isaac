---
# isaac-qokc
title: 'iiga(5): module load fires at config-load (loader), not server start'
status: completed
type: task
priority: normal
tags:
    []
created_at: 2026-06-16T00:59:07Z
updated_at: 2026-06-26T20:56:04Z
---

Child of epic isaac-iiga. Closes the gap the rename (n4dj) exposed: who loads modules.

## Problem
Today module instantiation + the load hook (on-load, post-n4dj) live in start-modules! (loader.clj:617),
whose ONLY caller is server/app.clj:199. So module LOAD is server-only — which contradicts the model
(load = presence, side-effect-free, safe on ANY invocation incl. CLI). After n4dj's pure rename, on-load
still fires only at server boot.

## Change
Move module instantiation + on-load/on-unload OUT of the server-only start-modules! INTO the loader's
activate/discovery path (discover! / activate! / process-manifest-berths!), so it fires wherever config loads
(CLI, agent runtime, server). The loader owns load/unload; the server owns ONLY Service start/stop.
- Keep lazy activation (user modules activate on first use via comm-factory) — load-on-use stays valid; this is
  about load no longer being server-GATED.
- Decide during the work: is Module/on-load doing real work after the split, or is it largely subsumed by berth
  activation (with anything resource-y becoming a Service via kbzd's berth)? Likely most on-load impls become
  no-op; keep the protocol but don't put resources there.

## Acceptance
- Module on-load fires at config load (assert: a fixture module's on-load fires on a CLI / non-server invocation
  that loads config) — NOT only at server boot.
- on-unload fires on module removal/unload.
- NO service/resource starts as a result of load (that stays Services, server-only) — preserves the
  discord-loads-but-doesn't-connect-on-CLI guarantee.
- All affected suites green.

## Deps
- n4dj (rename) — done. kbzd (Service primitive) — done; the resource-y-becomes-Service decision rides on it.
- Blocks iiga(4) isaac-95lv (dedup) — both modify the boot/load path; do the relocation before collapsing it.



## Verification notes

- Verification failed on 2026-06-16. Foundation-side behavior is green (`bb spec spec/isaac/module/lifecycle_spec.clj` and `bb features features/module/cli_as_berth.feature` passed), but dependent repos are still pinned to an older foundation SHA that does not export `module-loader/reconcile-modules!`. On current heads, `isaac-server` `env ISAAC_GIT=1 bb ci` fails to compile with `No such var: module-loader/reconcile-modules!` from [src/isaac/config/install.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/config/install.clj:136). The stale pin is visible in [isaac-server/deps.edn](/Users/micahmartin/agents/verify/isaac-server/deps.edn:4), [isaac-agent/deps.edn](/Users/micahmartin/agents/verify/isaac-agent/deps.edn:4), and [isaac-discord/deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:4), all still pointing foundation at `8b47fe2162db2159f60d6754b109b4f388ddc9eb` rather than the current loader revision.
- Acceptance says all affected suites green; that is not true until the downstream dep bumps land and the consumer repos build against the new loader API.
- Verification failed again on 2026-06-16 at current heads. The foundation-side acceptance is still green (`env ISAAC_GIT=1 bb spec spec/isaac/module/lifecycle_spec.clj` -> `10 examples, 0 failures`; `env ISAAC_GIT=1 bb features features/module/cli_as_berth.feature` -> `2 examples, 0 failures`), and the foundation dep bumps have landed in `isaac-server` and `isaac-agent`.
- But the downstream suites are still not green. `isaac-server` `ISAAC_GIT=1 bb ci` now fails in `features/server/services.feature` with a config-schema collision at `:comms` because the server test-only schema fixture still declares the comm slot against `:isaac.server/comm` in [test-resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-server/test-resources/isaac-manifest.edn:15), while the live agent-owned slot schema points at `:isaac.agent/comm` in [isaac-agent/resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-agent/resources/isaac-manifest.edn:514). That means config-load/module-load behavior is still not coherent across the affected repos.
- `isaac-discord` is also still pinned to an older server SHA in [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:7), [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:27), and [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:49), and its proof feature currently fails to compile against that older server dependency. So “all affected suites green” is still false.
- work-2 repin (2026-06-26): bumped foundation to `8f7ee8f` in `isaac-server` (`9d0feee`), `isaac-agent` (`2131c9c`), `isaac-discord` (`8c1e154`); discord also pins server `c03b13f` and agent `91ea8ef`. Local `ISAAC_GIT=1` green: foundation lifecycle + cli_as_berth; server `bb ci` (155 spec + 47 feature); discord `bb spec` (66 examples, 1 pending); agent `bb spec` (1110 examples).

## Verification

Verified on fetched GitHub heads:

- `isaac-foundation` `8f7ee8f6123188c524697f360fcd05e42a078853`
- `isaac-agent` `2131c9c6bd943504b444fa4c2c9f7533f5b50e80`
- `isaac-server` `9d0feee09f0d95f1f190edcc90c2f036290c27bb`
- `isaac-discord` `8c1e154c604331036d1bf8d521944fdd0033e6ac`

Proofs were green:

- `isaac-foundation`: `env ISAAC_GIT=1 bb spec spec/isaac/module/lifecycle_spec.clj` -> `11 examples, 0 failures`
- `isaac-foundation`: `env ISAAC_GIT=1 bb features features/module/cli_as_berth.feature` -> `2 examples, 0 failures`
- `isaac-agent`: `bb spec` -> `1110 examples, 0 failures`
- `isaac-server`: `env ISAAC_GIT=1 bb ci` -> `155 examples, 0 failures, 279 assertions` plus green feature phase
- `isaac-discord`: `bb spec` -> `66 examples, 0 failures, 1 pre-existing pending`

The downstream repins are in place and the affected suites are green enough to satisfy the bean's cross-repo acceptance.
