---
# isaac-5cr6
title: model-switch into a window smaller than the summary-prompt floor must still loop
status: completed
type: bug
priority: high
created_at: 2026-08-25T03:40:35Z
updated_at: 2026-08-25T19:30:22Z
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

## Planner direction (2026-08-25, Micah + plan)

Replace the mechanism test (`:chunked?`) with the property it stood in for:
**recheck iff compactable material remains after the splice.**

- Chunked splice → material remains → recheck (unchanged).
- `:oversized-single` splice → the oversized message itself was not
  summarized → material remains → recheck. This is the model-switch case.
- Complete non-chunked splice → nothing remains → skip. Preserves
  rubberband/slinky/quiet-day and the floor-stuck guard.

Implementation shape: where `feasible-chunks` yields `:oversized-single`,
mark the splice `:partial` the same way `:chunked` is marked today; one
predicate (`partial-splice?`) at the recheck site. Add a hard loop bound —
a pass that does not strictly reduce the estimate ends the loop — so a
window that can never fit the summary cannot spin.

## Implementation (2026-08-25, scrapper@isaac-work-1)

Recheck is now `partial-splice?` (chunked OR `:partial`) at `perform-compaction!`. `:no-progress` still stops when the estimate does not strictly reduce.

`:partial` is set only when `feasible-chunks` reports `:oversized-single` **and** some compactable body itself exceeds the window. Template-floor `:oversized-single` (summary request > window, messages themselves small) stays a complete splice so rubberband/slinky/quiet-day/rebound do not consume the next grover turn.

Un-wip'd model-switch. Isolated `:150`, rubberband `:50`, slinky `:84`, quiet-day `:49`, rebound `:194` green. `bb features` 726/0/1928 in 141.6s. `bb ci` specs 1570/0/3211 (3 pending) + features 726/0/1928 in 144.3s.

Acceptance:
- [x] model-switch green, `@wip` removed
- [x] isolated rubberband/slinky/quiet-day/rebound stay green
- [x] full `bb features` 0 failures under 180s
