---
# isaac-zmub
title: Schema defaults don't fill for a berth slice that's absent from config (jwks-alert-threshold nil → 500)
status: todo
type: bug
priority: high
created_at: 2026-10-01T00:49:18Z
updated_at: 2026-10-01T00:49:18Z
---

Found 2026-09-30 by the pin sweep (isaac-87nv, isaac-google). isaac-x9y5 declared `http.oidc.jwks-alert-threshold` with `:default 1` and dropped audit.clj's `(or … 1)` fallback. But foundation's `conform-berth-slices` only conforms berth slices already present in raw config, so with no `:http` section at all the default never fills: the threshold is nil and the JWKS-unreachable path NPEs on `(>= count nil)`, returning 500 instead of a fail-closed 401. isaac-google's push_door.feature hit it; the worker worked around it by adding `http.oidc.jwks-alert-threshold | 1` to that feature's Background (revert once fixed).

Isaac-dnib's ruling is that the loaded config is effective (defaults applied). An absent berth slice is the gap.

## Options (Micah to choose)
A. Foundation: conform every declared berth slice, absent ones as {}, so nested defaults fill (fixes it for every module; watch for absent slices now appearing in config get output).
B. isaac-http: restore the `(or … 1)` fallback in audit.clj (narrow; the schema default stays documentation).

Scenario TBD after the choice.

## Decision + Acceptance (Micah, 2026-09-30: option A; gated)

- Foundation conforms every declared schema section that is absent from config as {}, so nested defaults fill: both root-level :isaac.config/schema fragments (root conform) and config-berth-claimed slices (`conform-berth-slices`, the path isaac-http's :http takes). Required fields inside a wholly ABSENT section are not enforced (no validation error); a present section still enforces them.
- The @wip scenarios at the end of isaac-foundation `features/cli/config_defaults.feature` pass with @wip removed. Add a unit spec covering the berth-slice path (absent slice → defaults filled, no required error).
- Check isaac-http: with no :http section, `http.oidc.jwks-alert-threshold` resolves to 1 (spec or one-time check against the new foundation sha).
- `bb ci`, `bb features-slow`, `bb jvm-spec` green.
- Follow-up (separate, after landing): revert isaac-google push_door.feature's `http.oidc.jwks-alert-threshold | 1` workaround when google next pins foundation.

feature-baseline: isaac-foundation c13d496a13a6f2c0ddd76d05a19e13fcd2e05053
feature-blob: isaac-foundation features/cli/config_defaults.feature 01ac36959d44ff93989b6d6f6f7d9a6cea6e3338
