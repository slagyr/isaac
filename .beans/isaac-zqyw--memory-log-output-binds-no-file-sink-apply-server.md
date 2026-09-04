---
# isaac-zqyw
title: Memory log output binds no file sink (apply-server! under :memory)
status: completed
type: bug
priority: high
tags:
    - test-isolation
    - foundation
created_at: 2026-09-03T23:48:04Z
updated_at: 2026-09-04T00:04:17Z
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


## Implementation (2026-09-03, plan@isaac-plan)

isaac-foundation main @ **7c959041504bb24d3d67e154788bebf52f0be25f** (`7c95904`).
`apply-server!` under `:memory` → `lfile/clear-sink-config!`, no server sink;
docstring rewritten. Two new examples in `spec/isaac/log/output_spec.clj`
(preserves :memory + no sink; drops a prior file-mode sink). Gate: `bb spec`
894/0, `bb features` 139/0, lint clean. Pin this SHA in isaac-stao.

### Refinement (same session) — foundation main @ **e0dc789b58723a3415a12d5f0d95e0d9148bc316**

First cut (7c95904) broke six isaac-server `log_lifecycle.feature` scenarios
that assert the file on purpose (the harness sets `:memory` globally, so my
audit undercounted). Rule refined: harness `:memory` binds no sink UNLESS the
config names `:logging.output` explicitly — then it applies as in production.
Third spec example covers it. Gate: `bb spec` 895/0, `bb features` 139/0.
**Pin e0dc789 (not 7c95904).**

## CI follow-up (2026-09-03, scrapper)

GitHub Actions run `33819393077` failed on main at `7c95904` during `bb ci`, but
this bean already has a same-bean follow-up on main: `e0dc789`
(`isaac-zqyw: explicit :logging.output in config wins over harness :memory`).

Current `isaac-foundation` main @ `e0dc789` reproduces green locally:
- `bb ci` → specs `895 examples, 0 failures, 1622 assertions`; features `139 examples, 0 failures, 342 assertions`

No independent repair was commissioned; the CI regression is correlated to
`isaac-zqyw` and already resolved on default branch.

## Verification (2026-09-04, perceptor@isaac-verify)

Verified on `isaac-foundation` `origin/main` `e0dc789b58723a3415a12d5f0d95e0d9148bc316`.

Acceptance evidence:
- `bb spec spec/isaac/log/output_spec.clj` → `10 examples, 0 failures, 17 assertions`
- `bb spec` → `895 examples, 0 failures, 1622 assertions`
- `bb features` → `139 examples, 0 failures, 342 assertions`
- implementation is present in `src/isaac/log/output.clj`:
  - `apply-server!` preserves harness `:memory` with no server sink when `:logging.output` is absent
  - explicit `:logging.output` overrides harness `:memory`
- `spec/isaac/log/output_spec.clj` contains the coverage the bean requires:
  - preserves `:memory` and binds no server sink
  - explicit `:logging.output` wins over harness `:memory`
  - drops a prior file-mode sink on a later memory-mode boot
