---
# isaac-kk0o
title: isaac-agent CLI commands adopt the CLI host
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

Split from isaac-1fwl (child 2 of isaac-eqkb). Agent-only CLI host adoption.

Blocked by isaac-1fwl: Foundation `ensure-runtime!` installer memoization/retry and `:hosted` passthrough must be on main, and this repo's foundation pin must be advanced to that SHA before compile.

## Commands

| command | change |
|---|---|
| `sessions` | `install-cli!` (session/cli.clj:238) → `host/ensure-runtime!`; **delete `config/dangerously-install-config! nil` in the `finally`s (:277, :454)** — process host can do its own teardown; `builtin/register-all!` (:269) moves behind ensure-runtime! |
| `prompt` | `install-config!` (prompt_cli.clj:130), `runtime/install!` (:311), `builtin/register-all!` (:252) → ensure-runtime!; `user.dir` (:163,:202) → `host/cwd` |
| `auth`, `crew`, `turns` | `load-config!` → ensure-runtime!; `auth` read-line via host in; device-code poll loop checks `host/cancelled?` |

Each migrated command sets `:hosted true` on its `:isaac/cli` manifest entry.

Adopt foundation's `bb lint-cli-host` in this repo's `bb ci`.

## Fixture

`(host/run-embedded {:argv [...] :in (StringReader. "...") :out (StringWriter.) :err (StringWriter.) :root <root> :env {} :cwd <root>})` on a thread whose nexus is the live one (the spec installs a real config + store via the module's own `:install!` first, then snapshots the ambient config object, `(nexus/get-in [:sessions :store])`, and the tool registry, runs the command, and asserts identical objects after). **`sessions` must be run through this — the `finally` nil-out is the named regression.**

## Acceptance

A spec that runs each command via `host/run-embedded` with a live-runtime fixture and asserts (a) correct output/exit, (b) the ambient config snapshot, nexus `:sessions :store`, and tool registry are IDENTICAL objects before and after, (c) stdin-driven subcommands read from the supplied stream.

```
cd isaac-agent && bb lint-cli-host && bb ci
```

Do **not** migrate acp or other modules here. Do **not** land foundation. Pin bump is isaac-1fwl.
