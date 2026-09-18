---
# isaac-tdlz
title: Reserve :server for the process; HTTP config and bind logs move to :http
status: in-progress
type: feature
priority: high
tags:
    - http
    - config
    - server
created_at: 2026-09-18T01:34:41Z
updated_at: 2026-09-18T01:44:19Z
parent: isaac-3q4m
---

Clean cutover. `isaac server` the command and `logs/server.log` stay. `:server` in isaac.edn and bind fields on `:server/started` do not.

Parent: isaac-3q4m (server means the process). isaac-s9e3 already landed live token reload on the old `:server :auth :token` path; this bean renames that path.

## Decisions (2026-09-17, Micah)

1. **`:http {:host :port :auth :burst}`** — isaac-http schema.
2. **Top-level `:hot-reload`** — not `:config {:hot-reload}` (`isaac config set config.hot-reload` stutters). Foundation schema.
3. **`:bridge {:suspend-timeout-ms}`** — agent schema. Bridge already owns suspend.
4. **Foundation owns the retired `:server` table** (all former leaves, `[:retired? "use …"]`). One owner for the shell. HTTP does not keep contributing `:server`.
5. **`:server/started`** — process is up, no host/port. Emitted from `runner/start!`, not from `runner.cli` copying the HTTP component's bound port.
6. **`:http/listening`** — host and port. Emitted by the HTTP component after bind.
7. CLI `isaac server -p/-H` stay as process flags that override `:http` bind.
8. `features/server/` directory and `:server-runtime` component rename are a later pass.

## Implementation

- Foundation: top-level `:hot-reload`; retired `:server` map; move `:server/started` off `runner.cli` onto `runner/start!` (drop host/port); log-stream description is process logs, not "HTTP server logs".
- isaac-http: contribute `:http`; read `[:http …]` instead of `[:server …]`; log `:http/listening` after bind; sweep `server.host` / `server.auth.token` / `server.hot-reload` features and `cfg-fn` paths (including isaac-s9e3's live token, now `[:http :auth :token]`).
- Agent: `:bridge :suspend-timeout-ms`; stop reading `[:server :suspend-timeout-ms]`.
- isaac-hooks: `hooks.auth.token` retired hint becomes `use :http :auth :token`.
- Other modules: feature/config fixtures that still say `server.*`.

## Land order

1. isaac-foundation
2. isaac-http
3. isaac-agent, isaac-hooks, then remaining modules that mention `server.host` / `server.auth` / `server.hot-reload`
4. isaac registry only if a pin train is required

## Acceptance

@wip on each new feature file.

- `isaac-http` `features/http/config.feature:9` — :http bind/auth config is valid
- `isaac-http` `features/http/config.feature:22` — Retired `<key>` fails pointing at the new slot (outline)
- `isaac-foundation` `features/cli/server.feature:9` — Process start logs :server/started without host or port
- `isaac-http` `features/http/listening.feature:9` — HTTP bind logs :http/listening with host and port
- `isaac-http` `features/http/listening.feature:20` — Default port 6674 is on :http/listening

Superseded and removed from `isaac-http` `features/server/command.feature`: "server command logs startup with host and port" and "Default port is 6674 when no port is configured". Keep the hello scenario.

```
cd isaac-foundation && bb features features/cli/server.feature:9
cd isaac-http && bb features features/http/config.feature features/http/listening.feature
cd isaac-http && bb spec spec/isaac/http
cd isaac-foundation && bb spec spec/isaac/runner spec/isaac/main_spec.clj
cd isaac-http && bb ci
cd isaac-foundation && bb ci
```

Done when `@wip` is gone from those three files and the commands are green. Existing `server.*` fixtures in other modules must load or they are in this bean's sweep.

## Non-goals

Rename `isaac server`. Rename `logs/server.log` / `isaac logs server`. Dual-token overlap. Live rebind of host/port (isaac-03wy). `:server-runtime` rename. `features/server/` directory rename. Stopping `isaac config set` from logging token values.

## Work checkpoint (2026-09-18, scrapper@isaac-work-3)

Done:

- Foundation `bean/isaac-tdlz` @ `01e9a13`: base schema owns top-level `:hot-reload` and retired `:server` leaves; `runner/start!` emits process-only `:server/started`; runner CLI no longer adds bind fields; process log stream description updated. Focused schema/runner specs and `features/cli/server.feature:9` green.
- HTTP `bean/isaac-tdlz` @ `6517ecd`: manifest contributes `:http`; runtime reads HTTP host/auth and top-level hot reload; handler reads live `:http` auth/burst; listener emits `:http/listening`; old HTTP CLI no longer emits `:server/started`; focused HTTP schema/component/auth/burst/runtime specs green (33 examples, 69 assertions).

Red / next:

- `features/http/listening.feature` is still red in the existing harness: explicit `http.port 9876` is overridden by the harness ephemeral port (logged 61431), and the default scenario still observes a legacy `:server/started` with port from the foundation pin. Resume at `spec/isaac/http/server_steps.clj:382-416` to migrate harness bind resolution to `:http`/`:hot-reload`, then pin this bean's Foundation SHA into HTTP and rerun both HTTP features.
- After HTTP green: migrate Agent `src/isaac/agent/component.clj:45`, Hooks retired hint, sweep remaining module fixtures, run acceptance, rebase all branches, and hand off.
