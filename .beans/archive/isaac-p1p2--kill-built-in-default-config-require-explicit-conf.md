---
# isaac-p1p2
title: "Kill built-in default config; require explicit config on first run"
status: completed
type: feature
priority: normal
created_at: 2026-04-18T23:01:42Z
updated_at: 2026-04-19T22:40:32Z
---

## Description

The current loader falls back to a bundled default config (src/isaac/config/schema.clj:default-config) when no config files exist. This is deceptive — `isaac config` prints content the user never wrote, making it unclear what's real vs fabricated.

Remove the bundled default. Decide fail-fast behavior when no config is present.

## Behavior decisions (open — worker should surface before coding)

1. **What does `isaac config` print with no config?**
   - Empty map {}? — honest, shows there's nothing
   - Error "no config found, create ~/.isaac/config/isaac.edn"? — fail-fast

2. **What does `isaac chat` (or other workload commands) do with no config?**
   - Error out with guidance
   - Or implicitly fail on first config read

3. **Should we provide an `isaac config init` or similar bootstrap?**
   - Out of scope here, probably its own bead, but worth noting

## Scenario impact
Most AT scenarios start with:
  `Given an in-memory Isaac state directory "isaac-state"`

Under the current behavior, this yields a state dir that resolves to the bundled default config. Once defaults are removed, these scenarios will break unless they:
  - Add explicit `config file "isaac.edn" containing: ...`
  - Or the step activates a minimal config by default

Two approaches:
(a) Update the step to auto-write a minimal config when the scenario expects it. Add a separate "without config" step variant for edge cases.
(b) Update every scenario to add explicit config. Tedious but explicit.

Lean (a): step variants  — `Given an in-memory Isaac state directory "X"` (auto-writes minimal default) vs `Given an empty Isaac state directory "X"` (truly empty, for no-config tests).

## Source touchpoints
- src/isaac/config/schema.clj — delete default-config
- src/isaac/config/loader.clj — remove fallback path
- spec/isaac/features/steps/* — update state-dir step(s)
- All scenarios that rely on default behavior (most of features/)

## Motivation
Observed 2026-04-18: fresh isaac with no config prints a crafted default that looks like user config but isn't. Confusing for new operators; an honest "you haven't configured this yet" is better UX.

## Acceptance Criteria

1. src/isaac/config/schema.clj no longer contains bundled default-config
2. Loader returns empty (or errors) when no config files exist
3. isaac config on empty state produces honest output (either empty-map or guidance error)
4. isaac chat (and workload commands) fail clearly when no config is present
5. state-dir step(s) updated so existing scenarios either auto-provision minimal config or explicitly set it up
6. bb features passes
7. bb spec passes

