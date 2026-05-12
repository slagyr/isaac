---
# isaac-01h5
title: "Negative #index support in ordered-subsequence match steps"
status: completed
type: task
priority: low
created_at: 2026-04-22T17:48:15Z
updated_at: 2026-04-22T21:53:41Z
---

## Description

The #index meta column on table-matching steps (e.g. 'the outbound HTTP requests matches:', transcript-matching, file-lines-matching) currently takes positive integers. Extend to accept negative indices where -1 means the last entry, -2 the second-to-last, and so on.

Enables cleaner scenarios that assert 'the most recent X' without knowing the total count. Currently scenarios either lock a specific positive index (brittle to added entries) or omit ordering altogether.

Low-risk, one-step change to the match-object helper.

See gherclj step-defs in spec/isaac/features/steps/ for where #index is parsed. No new feature file needed — this is a step-infrastructure enhancement.

## Acceptance Criteria

1. Negative integer #index values resolve to 'count + N' position (-1 = last, etc.).
2. Out-of-range negative index fails with a clear error.
3. Remove @wip from the scenario in features/tools/built_in.feature.
4. bb features features/tools/built_in.feature passes.
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- #index column handler lives in the matcher helper (likely isaac.features.matchers or inside gherclj's match-object). Find the parse site for #index values.
- Current: positive int N means 'row N matches the Nth line (1-based)' — look up by index.
- New: negative int N means 'row matches the line at position (count + N)' where count is the total lines in the result. -1 = last, -2 = second-to-last, etc.
- Edge cases:
  - |N| > count → assertion fails with clear 'index out of range' message
  - Index 0 is invalid (ambiguous; indices are 1-based positive or negative)
  - Mixing positive and negative indices in one table is fine (e.g., row 1 uses index 1, row 2 uses index -1)
- Applies to all steps using #index: 'the tool result lines match:', 'session X has transcript matching:', future similar steps.

## Notes

Extended the shared feature matcher to support negative #index values from the end, kept tool-result line checks on substring semantics while adding negative #index there too, and removed the @wip scenario in features/tools/built_in.feature. Verified with bb features features/tools/built_in.feature, bb features, and bb spec in commits dfefabd and 1b546ad.

