---
# isaac-zqyw
title: Memory log output binds no file sink (apply-server! under :memory)
status: todo
type: bug
priority: high
tags:
    - foundation
    - test-isolation
created_at: 2026-09-03T23:48:04Z
updated_at: 2026-09-03T23:48:04Z
blocking:
    - isaac-stao
---

Repo: **isaac-foundation** (`src/isaac/log/output.clj`, `spec/isaac/log/output_spec.clj`).
Planning 2026-09-03 (Micah + plan), companion to isaac-stao.

## Problem

`apply-server!` preserves a harness-set `:memory` output but STILL binds the
durable server sink (`lfile/configure-server-sink!`), so every memory-mode
server boot in a feature suite writes `<root>/logs/server.log` as well as the
in-memory buffer. That was the isaac-3692 design (June 30: "memory AND file,
so file-lifecycle features could read the file"). Audit 2026-09-03: nothing
depends on it — no foundation spec asserts `server-sink?` under `:memory`
(output_spec covers only `:file` → sink and `:stdout` → no sink), and the
memory-mode server features (`hot_reload_logging.feature`,
`command.feature`) read only `the log has entries matching`. Combined with a
missing scenario root (isaac-stao) the file landed in the LIVE
`~/.isaac/logs/server.log` on zanebot. Principle (Micah): **tests log to
memory** — memory means no file.

## Change

- `apply-server!`: when `(log/output)` is `:memory`, leave the output alone
  and bind NO server sink. Also call `lfile/clear-sink-config!` so a sink left
  by an earlier file-mode boot in the same process is dropped.
- Docstring rewritten; note that this supersedes the isaac-3692 "memory and
  file" rationale.
- Spec: one new example in `output_spec.clj` `apply-server!`:
  "preserves :memory output and binds no server sink" — bind
  `log/set-output! :memory`, call `apply-server!`, assert `:memory` output and
  `(should-not (lfile/server-sink?))`. Existing `:file` / `:stdout` examples
  unchanged.
- No brew/Cellar release needed: production boots `:file`. isaac-server pins
  the resulting SHA in isaac-stao.

## Acceptance

    cd isaac-foundation
    bb spec spec/isaac/log/output_spec.clj
    bb spec && bb features

0 failures. Commit SHA recorded in the bean for isaac-stao's pin bump. Hand off
`--tag=unverified`, status stays in-progress.

## Out of scope

- The scenario-root injection in isaac-server (isaac-stao).
- CLI sink behaviour (`apply-cli!` already leaves `:memory` alone).
