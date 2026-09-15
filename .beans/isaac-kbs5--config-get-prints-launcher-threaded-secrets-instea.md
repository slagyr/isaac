---
# isaac-kbs5
title: config get prints launcher-threaded secrets instead of redacting
status: completed
type: bug
priority: high
tags:
    - cli
    - config
    - foundation
created_at: 2026-09-15T20:17:16Z
updated_at: 2026-09-15T20:58:13Z
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

## Implementation (2026-09-15, scrapper@isaac-work-1)

- Removed the unsafe printable-config shortcut that returned launcher-threaded resolved config verbatim.
- Preserved once-per-process resolution: printable output now uses the threaded load result and discovers `${VAR}` token names from its source files, then replaces matching resolved secret values with `<NAME:redacted>`.
- Kept the raw/reveal paths unchanged and activated the launcher-backed redaction scenario.
- Branch: `bean/isaac-kbs5` @ `520edd8356767999b641292caeae5e8b7df30593` (base `origin/main@a0a2b0f25bbdca9f391832ce0a11bc24137afd99`).

Verification run:

- `bb lint src/isaac/config/cli/common.clj spec/isaac/config/cli/common_spec.clj` — 0 errors, 0 warnings.
- `ISAAC_GIT=1 bb features features/cli/config_resolution.feature:84` — 1 example, 0 failures.
- `ISAAC_GIT=1 bb features features/cli/config_resolution.feature` — 6 examples, 0 failures; confirms one config resolution per real command remains intact.
- `bb spec spec/isaac/config/cli/common_spec.clj spec/isaac/config/cli/get_spec.clj` — 23 examples, 0 failures.
- `bb ci` — 1015 specs and 184 features, 0 failures (2 pre-existing pending scenarios). Cleared the recurring stale `~/.gitlibs/_repos/file/REL/fixture-agent` generated remote before the successful CI run.



## Landed on main (2026-09-15)

main-sha: isaac-foundation e6649143ac842b75c1640b4058379f1f46af9933



## CI hail (2026-09-15, hail 044d80c7)

GitHub run 35022394582 failed `bb spec` on squash `e664914` at `spec/isaac/log_viewer_spec.clj:344` (`tail! does not skip a line appended between the initial dump and follow seek`). Not caused by isaac-kbs5 (diff is printable-config redaction only; this spec is unchanged since 8b4a33b / 2026-09-10). Isolated `bb spec spec/isaac/log_viewer_spec.clj` failed locally on the same assertion — tracked as isaac-efb5 (todo). Prior main `a0a2b0f` (t1om) CI Tests was green (run 35019039103); same flake previously failed f21o CI (run 35018494124) then greened on `b374929`. No reopen, no independent repair.
