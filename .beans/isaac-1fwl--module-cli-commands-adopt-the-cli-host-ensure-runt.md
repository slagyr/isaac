---
# isaac-1fwl
title: 'Module CLI commands adopt the CLI host: ensure-runtime!, no ambient installs, acp/worksite fixes'
status: draft
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:24Z
updated_at: 2026-09-17T15:55:24Z
parent: isaac-eqkb
blocked_by:
    - isaac-dq4v
---

Child 2 of isaac-eqkb. Blocked by the host-library bean. Behavior in a plain process is unchanged; commands become SAFE to embed.

## Work, per repo (each adopts foundation's `bb lint-cli-host` in its `bb ci`)

| repo | command(s) | change |
|---|---|---|
| isaac-agent | `sessions` | `install-cli!` (session/cli.clj:238) → `host/ensure-runtime!`; **delete `config/dangerously-install-config! nil` in the `finally`s (:277, :454)** — process host can do its own teardown; `builtin/register-all!` (:269) moves behind ensure-runtime! |
| isaac-agent | `prompt` | `install-config!` (prompt_cli.clj:130), `runtime/install!` (:311), `builtin/register-all!` (:252) → ensure-runtime!; `user.dir` (:163,:202) → `host/cwd` |
| isaac-agent | `auth`, `crew`, `turns` | `load-config!` → ensure-runtime!; `auth` read-line via host in; device-code poll loop checks `host/cancelled?` |
| isaac-acp | `acp` | `set-snapshot!` (cli.clj:73), `nexus/register!`/`store/register!` (:171-172), `register-all!` (:209) → ensure-runtime!; stdin loop (:141) via host in; **remove `System/setProperty "user.dir"` (server.clj:32-47)** — pass cwd explicitly to `open-acp-session!`; **replace `--verbose` `with-redefs` (cli.clj:148)** with a binding/tap |
| isaac-hail | `hail` | stdin (cli.clj:62) via host in |
| isaac-episodes | `embed`, `recall`, `episodes` | `load-config!`/`runtime/install!` → ensure-runtime! |
| isaac-claude-code | `mcp-bridge` | stdin loop (cli.clj:113) + `ISAAC_SERVER_TOKEN` env via host |
| isaac-worksite | `worksites` | lock owner must not be bare PID (lock.clj:19-28): stamp an owner id (`pid` + stream/invocation id) so an embedded operator lock is distinguishable from the server's own turn locks; liveness = owner still registered, falling back to pid-alive for foreign pids |
| isaac-cli-proxy | `remote` | mark `:local-only true`; tty/stdin via host |
| isaac-foreman | `foreman` | lint only |

Also fix: isaac-server `http/cli.clj:119` defines `cli-api/run :server`, shadowing foundation's by load order — resolve to one owner (isaac-3q4m says foundation).

## Acceptance

Per repo, a spec that runs each command via `host/run-embedded` with a live-runtime fixture and asserts (a) correct output/exit, (b) the ambient config snapshot, nexus `:sessions :store`, and tool registry are IDENTICAL objects before and after (the `sessions` nil-out is the named regression), (c) stdin-driven subcommands read from the supplied stream. Worksite: spec that an embedded lock and a server turn lock in the same pid do not read as the same owner.

```
for r in isaac-agent isaac-acp isaac-hail isaac-episodes isaac-claude-code isaac-worksite isaac-cli-proxy isaac-foreman; do (cd $r && bb lint-cli-host && bb ci); done
```

Planner note: split per repo at dispatch time if a worker wants smaller units (agent / acp / rest is the natural 3-way cut). Draft until the fixture shape from child 1 is known.

Each migrated command sets `:hosted true` on its `:isaac/cli` manifest entry — the transitional marker child 3 reads to choose embedded vs subprocess; child 5 deletes it.
