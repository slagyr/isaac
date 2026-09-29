---
# isaac-gp4g
title: modules show reports a module's description, manual and contributions
status: todo
type: feature
priority: normal
created_at: 2026-09-29T15:58:40Z
updated_at: 2026-09-29T15:58:40Z
---

Design: Micah + planner, 2026-09-29. First bean toward **isaac-manual**, the ship's operating manual. isaac-manual is a module whose `manual__read` tool is generated from live config + installed modules, and whose `manual__configure` tool is the write operation, granted separately. This bean is the foundation layer it reads from.

## Wanted

- Manifest gains `:manual`: a string naming a classpath resource inside the module, which is its operating-manual doc (markdown; fixed `## Purpose` / `## Procedures` / `## Emergencies` headings, used later by isaac-manual). `:manual` is a reserved meta key, not a berth contribution.
- One introspection function in foundation returns, per installed module: `:description`, `:manual`, `:declares` (berth id → berth description), `:contributes` (berth id → contributed entry ids, or the value for unkeyed berths). `modules show` uses it; isaac-manual will too.
- `isaac modules show <name>` prints Description, Manual and Contributes (text), and the same fields in `--edn` / `--json`.
- A `:manual` that does not resolve on the module's classpath is a config **warning** (never an error).
- Update `modules show` help text to mention the new fields.

## Acceptance

Scenarios in `isaac-foundation/features/module/modules_show_manifest.feature` (4, `@wip` until done).

## Notes

- Planner check: the Background's local modules resolve. `modules show marigold.longwave` prints version/coord today, and each scenario fails on the new fields only.
- Friendly grouping ("Tools:", "Comms:") belongs to isaac-manual, not foundation; foundation reports berth ids.

## Likely repo scope

`isaac-foundation` (`isaac.module.manifest`, `isaac.module.berths`, `isaac.modules.cli`).

feature-baseline: isaac-foundation cde1a05792e62c721195a35f234af36cbae7798f
feature-blob: isaac-foundation features/module/modules_show_manifest.feature 1bb365592b3390012234fd7ecc45cbd8194e800d
