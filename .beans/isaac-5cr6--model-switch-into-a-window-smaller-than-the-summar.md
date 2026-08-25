---
# isaac-5cr6
title: model-switch into a window smaller than the summary-prompt floor must still loop
status: todo
type: bug
priority: high
created_at: 2026-08-25T03:40:35Z
updated_at: 2026-08-25T03:40:35Z
---

Split from isaac-zcb9. After suite-health repair, full `bb features` is 728/1 under 180s. The leftover is this model-switch scenario: expected compaction-count 2, got 1.

## Diagnosis (2026-08-25, scrapper@isaac-work-1)

`perform-compaction!` now rechecks only after a **chunked** splice that is still over threshold. That is load-bearing for rubberband/slinky counts and quiet-day summaries: a complete non-chunked splice must not consume the next grover/chat turn even when the prompt floor (soul + tools + nine-section template) keeps the estimate over the line.

This scenario's window is 20. The summary request is ~860–900 tokens, so `needs-chunking?` is true, but `feasible-chunks` returns nil when a single compactable is oversized (`:oversized-single`). The first splice is therefore **not** marked `:chunked`, recheck is skipped, and count stays 1.

The scenario comment (isaac-h5xm) assumed the first pass would be chunked and the recheck would fire. That assumption no longer holds against the current prompt floor.

## Goal

Make model-switch-into-smaller-window still loop until chat can continue **without** re-breaking non-chunked rubberband/slinky/quiet-day.

## Acceptance

- [ ] `features/session/compaction_logging.feature` scenario "Switching to a smaller-context model runs compaction repeatedly until chat can continue" is green and `@wip` is removed.
- [ ] Isolated rubberband (`compaction_strategies.feature:50`), slinky (`:84`), quiet-day (`compaction_memory_flush.feature:49`), rebound (`compaction_logging.feature:194`) stay green.
- [ ] Full `bb features` stays 0 failures under the 180s budget.

## Notes

- Do not revert the chunked-only recheck without a replacement that still skips non-chunked floor-stuck splices.
- Do not reopen migrate-session (rxr4).
