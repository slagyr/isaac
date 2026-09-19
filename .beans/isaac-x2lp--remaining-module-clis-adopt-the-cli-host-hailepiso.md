---
# isaac-x2lp
title: Remaining module CLIs adopt the CLI host (hail/episodes/claude-code/worksite/cli-proxy/foreman/server)
status: in-progress
type: feature
priority: high
tags:
    - cli
    - unverified
created_at: 2026-09-18T01:38:40Z
updated_at: 2026-09-19T18:28:01Z
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


## Promoted (planner, 2026-09-18)
isaac-1fwl (foundation leg) landed on foundation main cc53d69 — the blocker this bean named is satisfied; specs-only acceptance stands. Bump the foundation pin to main (not a bean-branch sha — see isaac-lsz2/isaac-j4jr).


## Checkpoint (scrapper@isaac-work-2)

Foundation pin target: `1afd934fff001cd9b75c4121546d961d96e6e02a` (origin/main).

| repo | branch | sha | status |
|---|---|---|---|
| isaac-hail | bean/isaac-x2lp | 25db2ef (base origin/main@8dbba29) | hail stdin via host/in, :hosted true, lint-cli-host, version 0.1.18 |
| isaac-cli-proxy | bean/isaac-x2lp | f372f6d (base origin/main@c31162f) | remote :local-only, tty/stdin via host, lint-cli-host, version 0.1.5 |
| isaac-episodes | bean/isaac-x2lp | (unstarted, worktree at a58929a) | next: pin + load-config!/runtime/install! → ensure-runtime! |
| isaac-claude-code | bean/isaac-x2lp | (unstarted, worktree at 50a8168) | mcp-bridge stdin + ISAAC_SERVER_TOKEN via host |
| isaac-worksite | bean/isaac-x2lp | (unstarted, worktree at 99ff219) | lock owner id + embedded vs server lock spec |
| isaac-foreman | bean/isaac-x2lp | (unstarted, worktree at a2057ae) | lint-cli-host only |
| isaac-server | bean/isaac-x2lp | (unstarted, worktree at cacb263, behind origin/main) | drop duplicate cli-api/run :server |

Resume: isaac-episodes-x2lp. Do not land foundation. Pin bump only.


## Checkpoint (scrapper@isaac-work-2, 2026-09-19)

Foundation pin target: origin/main @ df64bf15c739165c496cbf81e3d922ad3aa3346f.
Do not land foundation. Pin bump only. isaac-server/http :server already landed by isaac-66we — do not redo.

| repo | branch | sha | base origin/main | status |
|---|---|---|---|---|
| isaac-hail | bean/isaac-x2lp | fca0798 | c44c654 | hail stdin via host/in, :hosted, lint-cli-host, pin df64bf1, version 0.1.19 |
| isaac-episodes | bean/isaac-x2lp | cb4d910 | ba1a22f | embed/episodes/recall :hosted, ensure-runtime!, lint-cli-host, pin df64bf1, version 0.1.1 |
| isaac-claude-code | bean/isaac-x2lp | 4da98b5 | 50ee5f8 | mcp-bridge stdin via host/in, nonce via host/env, :hosted, lint-cli-host, pin df64bf1, version 0.1.13 |
| isaac-worksite | bean/isaac-x2lp | f01a492 | 99ff219 | lock owner pid+host identity; embedded vs turn distinct; :hosted; lint-cli-host; pin df64bf1; version 0.1.1 |
| isaac-cli-proxy | bean/isaac-x2lp | 5d7fd3e | 1f96845 | remote :local-only, host tty/stdin, lint-cli-host, pin df64bf1, version 0.1.5 |
| isaac-foreman | bean/isaac-x2lp | f9713d1 | 235f57c | lint-cli-host only, pin df64bf1, version 0.1.1 |
| isaac-server/http | — | d082206 on main | — | satisfied by isaac-66we; do not redo |

Handoff: `beans update isaac-x2lp --tag=unverified`; hail isaac-verify reply_to incoming hail.
