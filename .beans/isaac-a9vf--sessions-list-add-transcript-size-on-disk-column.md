---
# isaac-a9vf
title: 'Sessions list: add transcript size-on-disk column'
status: completed
type: feature
priority: normal
created_at: 2026-07-03T06:20:00Z
updated_at: 2026-07-03T18:33:14Z
---

## Problem

`isaac sessions list` currently shows age, last-input token usage, context
window, percent of window, crew, and tags. It does not show how large the
session transcript is on disk.

That makes it hard to distinguish:

- a session whose prompt accounting is large but whose retained transcript is
  still modest
- a session whose JSONL transcript has grown large on disk and may need
  operator attention

On zanebot, recent sessions like:

- `orchestration-plan` -> `452K`
- `isaac-verify` -> `792K`
- `orchestration-verify` -> `1.6M`

were easy to inspect with `du`, but that information is absent from the normal
operator view.

## Desired behavior

Add a size-on-disk column to `isaac sessions list`, sourced from the backing
transcript file for each session.

This should be an operator-facing metric, separate from token accounting.

## Likely repo scope

- `isaac-agent`
  - `src/isaac/session/cli.clj`
- session store helpers if the CLI needs a cleaner way to resolve transcript
  paths

## Notes

- The column should reflect transcript file size on disk, not cumulative token
  counters.
- Keep formatting compact and scan-friendly (`452K`, `1.6M`, etc.).
- This likely belongs next to the current context/token columns, but exact
  layout can be decided during implementation.

## Acceptance scenarios (committed @wip, 2026-07-03)

`isaac-agent/features/session/cli.feature`
- `@wip Scenario: sessions list shows one flat table sorted alphabetically with a CREW column`
- `@wip Scenario: sessions list output has aligned columns with a header row`
- `@wip Scenario: sessions list SIZE comes from transcript bytes, not token usage`

`isaac-agent/features/tagging/session_tags.feature`
- `@wip Scenario: isaac sessions list shows a Size column when tags are present`

Focused check:

```sh
cd isaac-agent && bb features features/session/cli.feature features/tagging/session_tags.feature
```

Definition of done:

- remove `@wip` from all four scenarios above
- update the two pre-existing baseline `features/session/cli.feature` scenarios so their expected plain-table header/alignment includes the new `SIZE` column
- `sessions list` shows a size-on-disk column in both the plain and tagged table layouts
- the size value is sourced from transcript bytes on disk, not token counters
- `bb features features/session/cli.feature features/tagging/session_tags.feature` passes

## Planner clarification (2026-07-03, prowl)

The bean's scope explicitly includes **updating the baseline session-list feature expectations** that cover the plain table layout. This is not optional fallout; those scenarios are part of the contract for the same surface and must move with the new column.

Required feature-set adjustments:

- Treat these existing plain-layout scenarios as in-scope acceptance for this bean:
  - `sessions list shows one flat table sorted alphabetically with a CREW column`
  - `sessions list output has aligned columns with a header row`
- Their expected output must be revised to include the new `SIZE` column in the plain layout.
- The size-specific scenario must continue to prove that `SIZE` comes from transcript bytes on disk rather than token counters.
- Tagged layout acceptance remains in-scope via `isaac sessions list shows a Size column when tags are present`.

No narrowing: the intended acceptance is the focused feature slice already named above, with the baseline list scenarios updated to the new contract.

## Worker notes (scrapper)

- Salvaged prior uncommitted a9vf WIP from `/Users/zane/agents/isaac/work-1/isaac-agent` and ported it cleanly onto current `isaac-agent` main as commit `df97091`.
- Implemented SIZE column in `isaac sessions list` for both plain and tagged layouts, sourced from transcript bytes via session transcript content rather than token counters.
- Removed `@wip` from the two a9vf acceptance scenarios.
- Focused spec passed: `clojure -M:spec spec/isaac/session/cli_spec.clj` (16 examples, 0 failures).
- Focused features remain red after implementation because older pre-existing session list expectations in `features/session/cli.feature` still assert the old column layout without SIZE, and one updated size scenario still needs regex alignment cleanup. Current focused feature run: `clojure -M:features features/session/cli.feature features/tagging/session_tags.feature` -> 3 failures (`sessions list shows one flat table sorted alphabetically with a CREW column`, `sessions list output has aligned columns with a header row`, `sessions list SIZE comes from transcript bytes, not token usage`).
- This bean therefore is not ready for verify yet; planning/requirements adjustment is needed because the committed acceptance scenarios for a9vf conflict with still-active baseline list scenarios that were not updated for the new column.


---

## Resolution (unverified — for verifier)

Implemented in `isaac-agent` **main** commit **19a7f9e** on top of prior landed SIZE-column work in **df97091**.

### What changed in this final pass

- updated the two pre-existing baseline plain-layout scenarios in `features/session/cli.feature` so their expected table/header patterns include the new `SIZE` column.
- kept the focused size-specific scenario proving `SIZE` is sourced from transcript bytes on disk rather than token counters.
- tagged-layout acceptance remains covered in `features/tagging/session_tags.feature`.
- no product-code change was needed in this pass; the implementation from `df97091` already rendered SIZE correctly. This pass completed the feature contract by aligning the older expectations with the new list layout.

### Verified

- `bb lint src/isaac/session/cli.clj`
  - 0 errors, 1 warning: existing unused binding `options` in `src/isaac/session/cli.clj`.
- `clojure -M:spec spec/isaac/session/cli_spec.clj`
  - green: **16 examples, 0 failures, 43 assertions**.
- `clojure -M:features features/session/cli.feature features/tagging/session_tags.feature`
  - green: **32 examples, 0 failures, 98 assertions**.
- `bb spec`
  - green: **1138 examples, 0 failures, 2237 assertions**.

### Scope summary

- plain sessions list layout now expects `SESSION AGE SIZE USED WINDOW PCT CREW`
- tagged sessions list layout continues to expect `Name AGE Size USED WINDOW PCT Crew Tags`
- size-on-disk remains separate from token accounting and is still formatted compactly (`B`, `K`, `M`)
