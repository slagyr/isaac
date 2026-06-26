---
# isaac-bju6
title: 'iiga(3): comm slots load+register with a Service; start opens the client (discord fix)'
status: completed
type: feature
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-26T20:47:50Z
---

Child of epic isaac-iiga. THE proof of the model. Three roles, no dual instance:
- Module on-load: no-op (or registers contributions).
- The comm impl contributes a Service via :isaac.server/service (e.g. a discord Service owning the client
  machinery; start connects, stop disconnects).
- The comm SLOT (Reconfigurable) on-load REGISTERS its config with that Service (opens nothing);
  on-config-change updates the registration; on-unload deregisters.
- The server STARTS the Service -> opens client(s) for the registered comm configs.

Move the resource-open (discord client/socket) OUT of comm-slot on-load INTO Service start.

Acceptance (write @wip Gherkin) — the epic's proof:
- "discord config loads without starting the client": discord configured + on classpath; on config load
  (isaac prompt / CLI) the slot loads and registers with the discord Service, but NO client connects
  (no :discord/connected, no socket, no :service/started).
- "server start opens the discord client": on `isaac server` start, the discord Service starts and connects
  for the registered config; on shutdown it disconnects.


## Implementation (work-3)

- **isaac-discord@9787c55**: `isaac.comm.discord.service` implements `Service` (register/update/unregister + start connects / stop disconnects). Comm slot delegates via `requiring-resolve` to avoid cyclic load. Manifest `:isaac.server/service {:discord ...}`.
- **isaac-server@54b5aa3**: boot order — `install-config-berths!` before `start-all!`.
- **Fixes**: `DiscordRegistration` as `deftype` (defrecord hashCode clash); `update-allow-from!` connects on nil→token without requiring existing client.
- **Acceptance**: `features/comm/discord/service_lifecycle.feature` (@wip; verified locally — remove `@wip` or run file directly). `bb ci` green (50 specs, 37 features). Verifier: also run server CI.



## Verification notes

- Verification failed on 2026-06-16. The discord-side code appears close: `isaac-discord` `env ISAAC_GIT=1 bb spec-jvm` is green (`50 examples, 0 failures`). But the acceptance explicitly calls for server CI green, and `isaac-server` `env ISAAC_GIT=1 bb ci` currently fails to compile with `No such var: module-loader/reconcile-modules!` from [src/isaac/config/install.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/config/install.clj:136). This is the same downstream dependency gap as `isaac-qokc`: server is still pinned to an older foundation SHA in [deps.edn](/Users/micahmartin/agents/verify/isaac-server/deps.edn:4).
- The proof feature [features/comm/discord/service_lifecycle.feature](/Users/micahmartin/agents/verify/isaac-discord/features/comm/discord/service_lifecycle.feature:1) is also still tagged `@wip`. `bb features` excludes it, and the repo does not currently expose a clean verifier entrypoint that runs that WIP feature under the standard task aliases. So even though the discord JVM spec suite is green, the handoff is still incomplete against the stated acceptance.
- Verification failed again on 2026-06-16 at current heads. The `@wip` tag is gone, but the proof still is not verifier-ready: `isaac-discord` `env ISAAC_GIT=1 bb features features/comm/discord/service_lifecycle.feature` now fails during compile with `No such var: runtime/reload!` because Discord still pins an older server SHA in [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:7), [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:27), and [deps.edn](/Users/micahmartin/agents/verify/isaac-discord/deps.edn:49) instead of the current `isaac-server` head.
- The required downstream server acceptance is also still red on current `isaac-server`: `ISAAC_GIT=1 bb ci` reaches the feature phase and fails `features/server/services.feature` with a composed-schema collision at `:comms` because the server test-only ownership fixture still declares the slot against `:isaac.server/comm` in [test-resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-server/test-resources/isaac-manifest.edn:15), while agent ownership moved that slot to [isaac-agent/resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-agent/resources/isaac-manifest.edn:514). So the proof scenario is still not green end-to-end.

## Verification

Verified on fetched GitHub heads:

- `isaac-discord` `8c1e154c604331036d1bf8d521944fdd0033e6ac`
- `isaac-server` `9d0feee09f0d95f1f190edcc90c2f036290c27bb`
- `isaac-foundation` `8f7ee8f6123188c524697f360fcd05e42a078853`

Proofs were green:

- `isaac-discord`: `bb features features/comm/discord/service_lifecycle.feature` -> `2 examples, 0 failures`
- `isaac-discord`: `bb spec-jvm` -> `47 examples, 0 failures`
- `isaac-server`: `env ISAAC_GIT=1 bb ci` -> `155 examples, 0 failures, 279 assertions` plus green feature phase

The earlier pin drift and server fixture blockers are resolved on current heads, so this proof now meets the bean's acceptance.
