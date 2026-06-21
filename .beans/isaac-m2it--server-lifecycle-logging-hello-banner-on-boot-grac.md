---
# isaac-m2it
title: 'Server lifecycle logging: hello banner on boot, graceful goodbye on shutdown'
status: todo
type: feature
priority: normal
created_at: 2026-06-21T23:13:15Z
updated_at: 2026-06-21T23:23:05Z
---

The server gives no visible lifecycle bookends. On boot it logs a thin `:server/started :host :port`; on stop it logs nothing — and `app/stop!` never even runs on a real service stop because no shutdown hook is registered (the process blocks on `@(promise)` and SIGTERM hard-kills it). Goal: a clear hello on boot and a graceful, logged goodbye on shutdown.

## 1. Hello banner on boot
Emit `:server/hello` in `isaac.server.cli/run` right after a successful `app/start!`, replacing the thin `:server/started` line. Structured log + human banner.

Fields (all already available):
- version — `isaac.foundation.version/version-string` (`isaac 0.1.7 (73ee5cc)`)
- runtime — `isaac.server.runtime/babashka?` -> `bb`|`jvm`; plus runtime-version (`babashka.version` prop for bb, `java.version` for jvm)
- root — root-dir
- host / port — from start! result
- dev? — resolved dev flag
- modules — count from module boot-stats
- pid — `(.pid (java.lang.ProcessHandle/current))` (works on bb and jvm)

Human banner:
```
Isaac 0.1.7 (73ee5cc) — hello
  runtime   jvm (java 25.0.2)
  root      /Users/zane/.isaac
  listen    127.0.0.1:8080
  dev mode  off
  modules   9 loaded
  pid       41207
```

## 2. Shutdown hook (behavioral fix)
Register `(.addShutdownHook (Runtime/getRuntime) (Thread. #(app/stop!)))` in the server run path before `block!`, so `app/stop!` runs on SIGTERM (launchd `service restart`/`stop`). Works under bb and jvm. Guard against double-stop (hook racing an in-process `stop!` / `start!`'s `(when (running?) (stop!))`) — `stop!` already no-ops on nil `@state`, but make the hook idempotent/race-safe.

## 3. Goodbye logging in app/stop!
`app/stop!` (app.clj:222) currently tears down delivery -> hail services -> module services -> scheduler -> config-source -> http with ZERO logs. Add:
- `:server/shutdown-starting`
- a per-service line as each component stops (delivery, services, modules, scheduler, config-source, http) — mirror the `:server/boot-phase` style
- final `:server/stopped` goodbye (with uptime if cheap)

## Acceptance
- Boot logs a single `:server/hello` carrying version, runtime (+version), root, host, port, dev?, modules, pid; human banner prints the same.
- `isaac service restart`/`stop` (SIGTERM) triggers `app/stop!`: logs show each service shutting down then `:server/stopped`.
- No double-shutdown when the hook and an in-process stop race.
- Verified on zanebot: restart the service and observe the full hello…goodbye sequence in server.log.

## Notes
- Surfaced 2026-06-21 alongside the JVM cutover; graceful SIGTERM matters more on the long-running JVM (delivery worker finishes its tick, Discord disconnects cleanly).
- Runtime detection helpers exist: `isaac.server.runtime/babashka?`, `normalize-runtime`.
- Pairs with the JVM runtime epic (isaac-5zfv) but is independent of it.

## Module close hooks (on-unload) — confirmed wired, but gated on stop!

Modules already have a close hook: `on-unload` on `isaac.module.protocol/Module`. Teardown path: `app/stop!` -> `module-loader/shutdown-modules!` -> `rollback-loaded-modules!` -> `module/run-unload!` -> `on-unload`, called in REVERSE load order with per-module error isolation (`:module/unload-failed`). Services tear down separately via `service-runtime/stop-all!` -> `run-stop!`.

Critical: these only fire when `app/stop!` runs — which is NEVER on a real service stop today (no shutdown hook; SIGTERM hard-kills). So the shutdown hook (item #2) is what actually makes module `on-unload` hooks fire on `service restart`/`stop`. Without it, every restart skips module + service teardown entirely.

Goodbye-logging refinement: `run-unload!`/`shutdown-modules!` log nothing on success (only `:module/unload-failed` on throw). The per-component shutdown logging must add a per-module unload line and a per-service stop line so the log shows each one closing. Two channels to surface: services (run-stop!) and modules (on-unload).

## Decision (2026-06-21): hello is an early, minimal greeting

`:server/hello` is emitted at the START of `cli/run` (replacing/absorbing `:server/boot-starting`), BEFORE the bind — so it carries only intrinsic process facts:
- version, runtime (+ runtime-version), root, dev?, pid

Dropped from hello: host, port, modules — these are logged later in boot (`:server/started` for listen address, `:server/boot-summary` for module count). No duplication.

Scenario 1 (locked pending final approval): assert log has `:server/hello` with runtime=bb (suite runs under bb), version/root/pid present (`#*`), dev?=false. Reuses existing steps (default Grover setup, config:, the Isaac server is started, the log has entries matching:) — NO new step defs; the new `:server/hello` event is the implementation deliverable.
