---
# isaac-zmub
title: Schema defaults don't fill for a berth slice that's absent from config (jwks-alert-threshold nil → 500)
status: draft
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
