---
# isaac-3nzp
title: 'Priority selector: --prefer recent/oldest (core + prompt)'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-26T21:50:05Z
updated_at: 2026-06-26T21:56:59Z
parent: isaac-4e4b
---

Add the `--prefer recent|oldest` priority/tiebreak to the shared selector and surface it on the prompt command (CLI side), per the isaac-4e4b REVISION (2026-06-26). Renames the confusingly-named `--resume` (which in other agents means our `--session`); `--session` stays the exact selector.

## Scope
- **Core** (isaac.session.selector): honor `:prefer recent|oldest` when `:reach :one` collapses a multi-match set. Today `pick-most-recent` is hardcoded (== recent); make it the default of a `:prefer` knob. No-op when the match is unambiguous (single match / explicit `--session`).
- **selector-cli**: add `--prefer` to the option-spec + `:prefer` into the flat opts/select map; validate value in {recent, oldest} with a clear error.
- **prompt** (bridge/prompt_cli): gains `--prefer`; REMOVE the `resolve-via-resume` bypass (`store/most-recent-session`) so `--prefer` flows through the shared selector. `--session` stays exact.

## DoD — the 4 @wip scenarios already committed to isaac-agent features/bridge/cli-prompt.feature
Remove their @wip tags when green:
- `--prefer oldest picks the oldest of multiple matching sessions`
- `--prefer recent picks the most recent of multiple matching sessions`
- `--prefer is a no-op when the match is unambiguous` (--session + --prefer allowed, exit 0)
- `--prefer with an unknown value errors clearly` (stderr "--prefer must be recent or oldest", exit 1)
Plus unit specs on the core :prefer tiebreak.

## Notes
Selection-suite, CLI side. Lowest-risk, mechanical. Empty-filter default (named prompt-default vs most-recent-of-all vs new) is a SEPARATE deferred decision, not this bean. Hail's --prefer (isaac-c58s) reuses the same core knob.

## Implementation (work-2, 2026-06-26)

- `isaac.session.selector`: `:prefer` (`:recent` default, `:oldest` tiebreak) via `pick-by-prefer`; `--resume` routes through a new resume-select branch (all sessions, same tiebreak).
- `isaac.session.selector-cli`: `--prefer` option, validation, `build-select` mapping.
- `isaac.bridge.prompt-cli`: removed `resolve-via-resume` / `most-recent-session` bypass; all selection flows through the shared selector.
- Removed `@wip` on four `cli-prompt.feature` scenarios; oldest scenario re-asserts `updated-at` after transcript seed (append bumps timestamps).
- **SHA:** isaac-agent `47e038c`
- **Verified:** selector + selector-cli + prompt_cli specs 51/0; `cli-prompt.feature` 29/0 (583 examples in full features run).
