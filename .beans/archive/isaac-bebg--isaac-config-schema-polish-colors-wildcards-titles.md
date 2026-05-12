---
# isaac-bebg
title: "isaac config schema polish: colors, wildcards, titles, error messages"
status: completed
type: bug
priority: low
created_at: 2026-04-19T23:33:27Z
updated_at: 2026-04-20T00:05:50Z
---

## Description

Review pass on `isaac config schema` surfaced a cluster of small UX issues. Fix as one bead since they're interrelated and all in the same command surface.

## Issues

1. **No ANSI colors.** Output is plain text even though schema.term supports colors via :color? true. Either the CLI passes :color? false or something is stripping escape codes. Default to colored when stdout is a TTY.

2. **`crew.id` fails** where it should succeed. Drilling to a specific field of an entity via the entity schema name doesn't work. `config schema crew.id`, `config schema crew.model`, `config schema providers.base-url` should all print the field schema.

3. **`providers._` yields 'Path not found'.** The `_` wildcard sentinel (shell-safe alternative to `*`) isn't wired up. Should resolve to the :value-spec entity template. Same goes for `crew._`, `models._`.

4. **`providers._d` crashes** (stack trace) instead of printing 'Path not found in config schema: providers._d'. Any invalid trailing segment should produce the friendly error.

5. **`provider` (singular, no wildcard) surprisingly works** — likely because the implementation falls through to entity-schemas keyed by :provider. This is inconsistent with the path-based grammar. Decision: either error out (consistent — sections are plural), or make it an alias for `providers._` (convenient). Worker picks; document the decision.

6. **Root schema title should read 'isaac config schema'** instead of the generic 'Schema'. Matches the brand.

7. **Section schemas should be titled '<name> config schema'** — e.g., 'crew config schema', 'providers config schema'. Current output just says the section name.

8. **Output should echo the requested path** in the title so the user knows what they asked for. Suggested: 'isaac config schema <path>' as the title when a path is supplied. Without a path, just 'isaac config schema'.

## Scope
- src/isaac/cli/config.clj: fix color passing, path routing, wildcard (_) handling, title formatting
- src/isaac/config/schema/term.clj: may need a :title option to support the new title format (instead of hardcoding 'Schema')
- Update features/cli/config.feature schema scenarios where the new titles/behavior matters

## Acceptance Criteria

1. isaac config schema (no args) titles output 'isaac config schema'
2. isaac config schema crew (or any section) titles output 'crew config schema'
3. isaac config schema crew.id prints the :id field schema (or crew.model, providers.base-url, etc.)
4. isaac config schema providers._ resolves to the provider entity template
5. isaac config schema providers._d prints 'Path not found in config schema: providers._d' and exits 1 (no stack trace)
6. Output includes ANSI color when stdout is a TTY
7. Decision on bare 'provider' (singular) recorded either as 'alias' or 'error'
8. bb features passes
9. bb spec passes

## Notes

Eight @wip scenarios updated/added in features/cli/config.feature (47d4fe3). These are the spec — remove @wip as work lands.

Title structure formalized: <path> [(<name> entity)] config schema. Root is the exception ("isaac config schema").

Added #11 (section vs entity) and #12 (guidance) to scope.

Not covered by scenarios:
- ANSI color (dropped from acceptance — left as open decision)
- Bare 'provider' singular handling (worker picks: alias or error)

