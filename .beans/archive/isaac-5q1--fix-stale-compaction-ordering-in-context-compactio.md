---
# isaac-5q1
title: "Fix stale compaction ordering in context/compaction.feature"
status: completed
type: bug
priority: high
created_at: 2026-04-16T22:34:32Z
updated_at: 2026-04-16T22:38:29Z
---

## Description

The 'partial compaction' scenario at features/context/compaction.feature:90-114 expected compaction at position 3 (middle). Slinky semantics and the implementation place it at position 1 (top), matching features/session/compaction_strategies.feature:70 and features/session/async_compaction.feature:42.

The expected-match table has been corrected in commit 2d09942. This bead tracks verification so isaac-cdz can close.

Feature: features/context/compaction.feature (scenario at line 90)

## Acceptance Criteria

1. bb features features/context/compaction.feature:90 passes (1 example, 0 failures)
2. bb features passes (full suite, 0 failures)
3. isaac-cdz unblocked and ready to close

