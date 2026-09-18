---
# isaac-ow5u
title: isaac-acp CLI command adopts the CLI host
status: todo
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-18T01:38:40Z
updated_at: 2026-09-18T04:57:13Z
parent: isaac-eqkb
blocked_by:
    - isaac-1fwl
---

Split from isaac-1fwl (child 2 of isaac-eqkb). ACP-only CLI host adoption.

Blocked by isaac-1fwl: Foundation `ensure-runtime!` installer memoization/retry and `:hosted` passthrough must be on main, and this repo's foundation pin must be advanced to that SHA before compile.

## Command

| command | change |
|---|---|
| `acp` | `set-snapshot!` (cli.clj:73), `nexus/register!`/`store/register!` (:171-172), `register-all!` (:209) → ensure-runtime!; stdin loop (:141) via host in; **remove `System/setProperty "user.dir"` (server.clj:32-47)** — pass cwd explicitly to `open-acp-session!`; **replace `--verbose` `with-redefs` (cli.clj:148)** with a binding/tap |

Set `:hosted true` on the `:isaac/cli` manifest entry.

Adopt foundation's `bb lint-cli-host` in this repo's `bb ci`.

## Fixture

`(host/run-embedded {:argv [...] :in (StringReader. "...") :out (StringWriter.) :err (StringWriter.) :root <root> :env {} :cwd <root>})` on a thread whose nexus is the live one. Spec installs a real config + store via `:install!` first, snapshots ambient config / nexus / tool registry, runs the command, asserts identical objects after. Stdin-driven loop reads from the supplied stream.

## Acceptance

A spec that runs `acp` via `host/run-embedded` with a live-runtime fixture and asserts (a) correct output/exit, (b) ambient config snapshot, nexus, and tool registry are IDENTICAL objects before and after, (c) stdin is the supplied stream, (d) no `user.dir` property write, (e) `--verbose` does not `with-redefs`.

```
cd isaac-acp && bb lint-cli-host && bb ci
```

Do **not** migrate agent or other modules here. Do **not** land foundation. Pin bump is isaac-1fwl.


## Promoted (planner, 2026-09-18)
isaac-1fwl (foundation leg) landed on foundation main cc53d69 — the blocker this bean named is satisfied; specs-only acceptance stands. Bump the foundation pin to main (not a bean-branch sha — see isaac-lsz2/isaac-j4jr).
