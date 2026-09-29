---
# isaac-ppyj
title: Rename the manifest :manual key to :handbook
status: in-progress
type: task
priority: normal
created_at: 2026-09-29T20:24:44Z
updated_at: 2026-09-29T20:27:12Z
---

Ruling: Micah, 2026-09-29. The module is **isaac-handbook** (after the POH, the Pilot Operating Handbook; repo slagyr/isaac-handbook). "Manual" is retired. A module's operating doc is its **handbook**.

## Wanted

Rename the manifest key gp4g added, clean cutover (no alias; `:manual` becomes an unknown/contribution key like any other stray namespaced-less key would):

- Manifest key `:manual` → `:handbook` (manifest schema + known meta keys, `src/isaac/module/manifest.clj`).
- Discovery warning: `manual-resolves?` / `manual-warnings` / `manual-warning` → handbook names, and the warning key → `module-index["<id>"].handbook` (`src/isaac/module/discovery.clj`).
- `module-report` returns `:handbook` instead of `:manual` (`src/isaac/module/berths.clj`).
- `modules show`: text label `Handbook:` (aligned like the other labels), `--edn`/`--json` field `handbook`, and help text (`src/isaac/modules/cli.clj`).
- Docstrings/comments: "operating-manual doc" → "handbook doc"; the doc's conventional name is `handbook.md` with `## Purpose` / `## Procedures` / `## Emergencies` headings. isaac-manual → isaac-handbook wherever it's mentioned.
- Specs updated to match.

## Acceptance

`isaac-foundation/features/module/modules_show_manifest.feature`: the 4 gp4g scenarios, renamed and back on `@wip` under this bean.

## Notes

- gp4g (completed) keeps passing its own gate: `verify` checks gp4g's recorded main-sha, not the current file.
- `grep -rni manual src spec features` in foundation must come back empty afterwards (unrelated uses of the word excepted).
- No other repo uses `:manual` yet.

## Likely repo scope

`isaac-foundation` only.

feature-baseline: isaac-foundation 64c620cb5e9978dc25326b3ab5a246cf6e444da0
feature-blob: isaac-foundation features/module/modules_show_manifest.feature 91f4bb72fd5d7a4e3b45bd8634e7fd374a4e781f
