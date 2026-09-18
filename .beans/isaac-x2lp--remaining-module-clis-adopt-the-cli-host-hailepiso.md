---
# isaac-x2lp
title: Remaining module CLIs adopt the CLI host (hail/episodes/claude-code/worksite/cli-proxy/foreman/server)
status: draft
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-18T01:38:40Z
updated_at: 2026-09-18T01:39:34Z
parent: isaac-eqkb
blocked_by:
    - isaac-1fwl
---

Split from isaac-1fwl (child 2 of isaac-eqkb). Remaining module CLIs + isaac-server `:server` command owner.

Blocked by isaac-1fwl: Foundation `ensure-runtime!` installer memoization/retry and `:hosted` passthrough must be on main, and each repo's foundation pin must be advanced to that SHA before compile.

## Work, per repo

| repo | command(s) | change |
|---|---|---|
| isaac-hail | `hail` | stdin (cli.clj:62) via host in |
| isaac-episodes | `embed`, `recall`, `episodes` | `load-config!`/`runtime/install!` → ensure-runtime! |
| isaac-claude-code | `mcp-bridge` | stdin loop (cli.clj:113) + `ISAAC_SERVER_TOKEN` env via host |
| isaac-worksite | `worksites` | lock owner must not be bare PID (lock.clj:19-28): stamp an owner id (`pid` + stream/invocation id) so an embedded operator lock is distinguishable from the server's own turn locks; liveness = owner still registered, falling back to pid-alive for foreign pids |
| isaac-cli-proxy | `remote` | mark `:local-only true`; tty/stdin via host |
| isaac-foreman | `foreman` | lint only |
| isaac-server | `server` | `http/cli.clj:119` defines `cli-api/run :server`, shadowing foundation's by load order — resolve to one owner (isaac-3q4m says foundation) |

Each migrated command (except lint-only foreman) sets `:hosted true` on its `:isaac/cli` manifest entry. `remote` is `:local-only true` (not hosted over the pipe).

Adopt foundation's `bb lint-cli-host` in each module `bb ci`.

## Fixture

`(host/run-embedded …)` live-nexus fixture as on isaac-1fwl. Worksite: spec that an embedded lock and a server turn lock in the same pid do not read as the same owner.

## Acceptance

Per repo, a spec that runs each command via `host/run-embedded` with a live-runtime fixture and asserts (a) correct output/exit, (b) ambient config / nexus / tool registry IDENTICAL objects before and after, (c) stdin-driven subcommands read from the supplied stream. Worksite lock-owner spec as above. Server: one `cli-api/run :server` owner (foundation).

```
for r in isaac-hail isaac-episodes isaac-claude-code isaac-worksite isaac-cli-proxy isaac-foreman isaac-server; do (cd $r && bb lint-cli-host && bb ci); done
```

(foreman: lint-cli-host + bb ci; skip run-embedded if lint-only.)

Do **not** migrate agent or acp here. Do **not** land foundation. Pin bump is isaac-1fwl.
