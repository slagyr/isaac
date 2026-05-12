---
# isaac-43lr
title: "isaac config set / unset: write and remove config values"
status: completed
type: feature
priority: normal
created_at: 2026-04-19T23:04:51Z
updated_at: 2026-04-19T23:49:08Z
---

## Description

Add `isaac config set <path> <value>` and `isaac config unset <path>` subcommands. Completes the CRUD surface on Isaac's config (get/set/validate/unset).

## Routing rules
When the path already exists in either isaac.edn or an entity file, write there (follow established pattern). When new, consult the root config flag `:prefer-entity-files` (boolean, default false):
- false → write to isaac.edn
- true → write to entity file at crew/<id>.edn, models/<id>.edn, etc.

## Soul-specific handling
Soul has a companion .md file alternative to inline :soul. Routing for `config set crew.<id>.soul`:
- If crew/<id>.md exists → update the .md
- Else if inline :soul exists → update inline
- Else new: if value > 64 chars → create .md; else → inline

## Validation
`set` runs full schema validation before writing. If the result would be invalid (semantic errors like references to undefined entities), refuse the write and report errors to stderr. Unknown keys produce warnings but still write (consistent with validate's unknown-key policy).

## Unset behavior
- Removes the value from the file where it lives
- If removing empties an entity file, delete the file
- Removing a required field → validation error, refuse (caught by the same validate-before-write path)

## Scenarios
features/cli/config.feature — 12 @wip scenarios under 'Set' and 'Unset' sections.

## Depends on
- isaac-soyn (uses c3kit.apron.schema.path for path parsing — same grammar as get)
- c3kit.apron.schema coerce/validate (already in apron 2.6.0)

## Acceptance Criteria

1. Remove @wip from the 12 set/unset scenarios in features/cli/config.feature
2. bb features features/cli/config.feature passes
3. bb features passes
4. bb spec passes
5. :prefer-entity-files added to root schema with default false
6. set and unset listed in config help output

## Notes

Extended with stdin support for whole-entity set (commit b7072ce):

- isaac config set <path> - reads an EDN value (scalar or entity map) from stdin
- Value replaces whatever is at <path> — no merging
- Matches the validate --as pattern for agent editing workflows
Log assertion template added to the basic set scenario (b756898). Log shape:

| level | event       | path              | value | file       |
| :info | :config/set | crew.marvin.model | :gpt  | isaac.edn  |

Worker should apply the same pattern to the remaining set/unset scenarios (one log entry per mutation):
- :config/set — for writes (include path, value, file)
- :config/unset — for removals (include path, file)
- :config/set-failed — for validation refusals (include path, error)

Event kw uses :config/ prefix to match the convention used elsewhere (:session/compaction-check, etc.).

