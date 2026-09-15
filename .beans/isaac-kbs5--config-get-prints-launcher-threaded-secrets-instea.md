---
# isaac-kbs5
title: config get prints launcher-threaded secrets instead of redacting
status: todo
type: bug
priority: high
tags:
    - foundation
    - cli
    - config
created_at: 2026-09-15T20:17:16Z
updated_at: 2026-09-15T20:17:16Z
---

`isaac config get` already redacts `${VAR}` values (`<NAME:redacted>`) and `--reveal` prints them after typing REVEAL. In-process feature tests cover that.

The packaged CLI still prints secrets: the launcher (v1la) threads an already-substituted config into `config get`, and `printable-config` returns that snapshot without redacting. Feature tests call `main/run` without that snapshot, so they stay green.

## Decision

- Decision (2026-09-15, Micah): `config get` (and the same printable path) must redact even when opts already carry a resolved `:config`. Print from raw tokens vs resolved values — do not emit a launcher-threaded substituted snapshot.

## Scope (isaac-foundation)

- `isaac.config.cli.common/printable-config` — drop the unredacted threaded-config shortcut (or redact it against raw).
- Rewrite `spec/isaac/config/cli/common_spec.clj` "printable-config reuses a non-empty :config from opts" — that spec currently *requires* the leak.

## Scenarios

`features/cli/config_resolution.feature` @ b374929

- `:74` launcher-backed config get still redacts `${VAR}` values — **new @wip** (`the isaac launcher is run with "config get providers.anthropic.api-key"`)

Existing in-process redact rows in isaac-agent `features/config/cli.feature` stay.

New steps invented: none.

At landing: remove `@wip` from `:74`.

## Exceptions

Authorized (2026-09-15): add the launcher-backed redact scenario to `config_resolution.feature`.

## Acceptance

```
cd isaac-foundation
ISAAC_GIT=1 bb features features/cli/config_resolution.feature:74
bb spec spec/isaac/config/cli/common_spec.clj spec/isaac/config/cli/get_spec.clj
bb ci
```
