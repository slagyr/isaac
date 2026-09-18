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

- Foundation `bean/isaac-tdlz` @ `c5ac437` (base `origin/main@cc53d69`): base schema owns top-level `:hot-reload` and retired `:server` leaves; `runner/start!` emits process-only `:server/started`; runner CLI no longer adds bind fields; process log stream description updated. Focused schema/runner specs and `features/cli/server.feature:9` green.
- HTTP `bean/isaac-tdlz` @ `22d4c04` (base `origin/main@1245bfb`): manifest contributes `:http`; runtime reads HTTP host/auth and top-level hot reload; handler reads live `:http` auth/burst; listener emits `:http/listening`; duplicate HTTP `:server` CLI owner and old startup log removed; harness/config specs migrated; Foundation branch pinned. Green: listening feature (2/3 assertions), config valid scenario (1/4), all `spec/isaac/http` (102/170).
- Agent `bean/isaac-tdlz` @ `a38d403`: `:bridge :suspend-timeout-ms` schema/read and component specs green.
- Hooks `bean/isaac-tdlz` @ `b764fd6`: retired hint and feature fixtures point to `:http`.

Red / next:

- Full HTTP config feature cannot parse its planner-authored Scenario Outline example strings under gherclj 1.3.0 (quoted EDN is injected into generated test names/forms). First scenario is green; retire validation is covered by Foundation schema spec. Do not rewrite the feature beyond authorized `@wip` removal without verifier/planner approval.
- Resume remaining fixture sweep at `isaac-claude-code/src/isaac/llm/api/claude_cli.clj:374` (running server port/token map), then `features/llm/api/claude_driver.feature:289`; sweep cli-proxy and isaac-server fixtures; run repository acceptance; rebase all four product branches and hand off.
