---
# isaac-zmub
title: Schema defaults don't fill for a berth slice that's absent from config (jwks-alert-threshold nil → 500)
status: completed
type: bug
priority: high
created_at: 2026-10-01T00:49:18Z
updated_at: 2026-10-01T02:14:41Z
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

## Landed on main (2026-10-01)

main-sha: isaac-foundation 10a8844e66efc3d557318fd83be4a6239a167d5a

Gate PASS (both before and after the squash-merge). CI green on all 3
jobs (Server boot with a module-provided config type — checks out
isaac-http too; verify / bb ci; Slow features).

Root cause: apron's own conform never descends into a nested :map
field whose value is nil (an absent section), so a schema's nested
:default never fills unless the section is already present — true both
for a root-level :isaac.config/schema fragment (the :beacon fixture)
and a config-berth-claimed slice. isaac-http's :http turned out to
route through the ROOT conform path (no factory on its schema → not a
berth-claimed slice), not through conform-berth-slices as first
suspected — but the fix generalizes both paths per the Decision, and
`conform-berth-slices` has its own absent-slice fill + unit-spec
coverage for the slice path too.

Fix: `schema-base/conform-absent-section` conforms a synthetic {}
against the section's spec and drops (via `drop-required-errors`) any
embedded required-field error — nothing was configured, so it isn't a
validation failure. Wired into both `-validate-root-config` (root
sections) and `conform-berth-slices` (berth slices).

isaac-http check: with the dev-local alias pointed at the bean
worktree, a direct probe (`loader/load-config-result` with `{}` root
config, real fs) showed `http.oidc.jwks-alert-threshold` = 1, zero
errors, isaac.http present via classpath builtin discovery. Reverted
the dev-local + temporary feature-scenario edits in isaac-http;
nothing committed there.

Test fallout: the shared `marigold.clj` :watch fixture combined a
:default with an existence validation (:berth-exists?/:gauge-exists?)
that no scenario had ever exercised through the "absent" path (every
:watch scenario set it explicitly, or used it only for schema-display
text). Once absent sections fill, that default surfaced as a bogus
"references undefined berth/gauge" error in ~25 unrelated specs.
Removed the two :default values from the fixture (kept the
validations, since semantic_errors_spec.clj / cli/validate_spec.clj
rely on them with explicit values) and moved term_spec.clj's "shows
default" assertion onto an inline spec literal. No other fixture or
production schema had this combination.

Follow-up still open per the bean: isaac-google's push_door.feature
workaround revert, once google next pins this foundation sha.
