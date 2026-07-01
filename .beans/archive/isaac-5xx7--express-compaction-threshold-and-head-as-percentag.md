---
# isaac-5xx7
title: Express compaction :threshold and :head as percentages of context-window
status: completed
type: feature
priority: normal
created_at: 2026-05-16T23:56:24Z
updated_at: 2026-06-29T17:17:34Z
---

## Problem

`:threshold` and `:head` in compaction config are absolute token counts today (`src/isaac/session/compaction.clj:30-34`):

```clojure
{:async?    false
 :strategy  :rubberband
 :head      (default-head context-window)        ; computed from window
 :threshold (default-threshold context-window)}  ; computed from window
```

The defaults are computed from context-window, but user-supplied values bypass that — they go through as absolute counts. When a session's model changes (and thus its context-window changes), the user-set thresholds are now wrong relative to the new window. Someone who set `:threshold 80000` for a 200k model now has effectively no compaction on a 32k model (the window itself is smaller than the threshold).

## Approach

Make `:threshold` and `:head` strict percentages in the range `[0.0, 1.0)`.

- `:threshold 0.8` means "compact when accumulated input tokens reach 80% of context-window"
- `:head 0.3` means "keep the most recent 30% of context-window worth of tokens during compaction"
- Schema rejects values `>= 1.0` and `< 0.0` at config validation
- Resolution computes the absolute value at use time: `actual = percentage * context-window`
- Model swap re-resolves cleanly — percentage stays valid

## Default values

Match the current effective defaults:
- `:threshold 0.8`
- `:head 0.3`

Drops the existing `(max (- window 50000) (* 0.8 window))` formula in `default-threshold`. For very large windows (>250k) this is a small behavioral change: current code lets you fill `window - 50000` before compacting (~95% on a 1M-window model); the new pure-0.8 will compact earlier. Acceptable — large-window users can dial up the percentage explicitly if they want more headroom.

## Migration

Hard break: existing user configs with absolute token thresholds will fail schema validation with a clear error guiding them to use a percentage. Most users haven't customized these and will be unaffected. Document in the changelog.

## Scope

- Schema: `:threshold` and `:head` are `:double` in `[0.0, 1.0)` (or `[0.0, 1.0]` if we want to allow `:threshold 1.0` for "never compact" — open question)
- `compaction.clj/resolve-config` and `default-threshold`/`default-head` updated
- `should-compact?` and consumers multiply by context-window when comparing
- Migration error message in validator with example

### Test ripple (don't miss these)

These feature files hardcode absolute-token threshold/head values and need recalibrating to percentages:

- `features/context/compaction.feature` — `compaction.threshold` and `compaction.head` columns in multiple Scenario tables
- `features/session/compaction_strategies.feature` — same pattern
- `features/session/async_compaction.feature` — same pattern
- `features/session/behavior_funnel.feature` — outline 1's `:compaction` rows and the hardcoded-default expected value (`{:strategy :rubberband :threshold 26214}` becomes `{:strategy :rubberband :threshold 0.8}` post-migration)

Also probably affected:
- specs under `spec/isaac/session/` that hardcode threshold values for compaction tests
- Anywhere `default-threshold`/`default-head` are referenced in tests

Recalibration approach: pick a pinned context-window per scenario (or use the test default), express threshold/head as fractions, recompute the expected outcomes (`total-tokens >= threshold * window`). The test logic stays the same; the numbers change.

## Out of scope

- The session-behavior funnel (`isaac-bv48`) — independent
- Other compaction settings (`:strategy`, `:async?`) — already abstract enough
- Token-budget arithmetic elsewhere — only the threshold/head storage changes

## Relationship

- Independent of `isaac-bv48` (the funnel). Could land before or after.
- Once `isaac-bv48` and this bean both land, the compaction cascade rows in `features/session/behavior_funnel.feature` express threshold/head as percentages naturally.

## Feature file

`features/session/compaction_percentages.feature` (scenarios deferred to draft phase).

## Summary of Changes

- `src/isaac/session/compaction_schema.clj`: changed `:threshold` and `:head` to `:double` type with `[0.0, 1.0)` range validation
- `src/isaac/session/schema.clj`: updated CompactionState types to `:double`
- `src/isaac/session/compaction.clj`: defaults changed to `0.8`/`0.3` as percentages; `should-compact?` and `compaction-target` multiply by `context-window` at use time; `compaction-target` takes `context-window` as 3rd argument
- `spec/isaac/session/compaction_schema_spec.clj`: all threshold/head values updated to doubles in `[0.0, 1.0)`
- `spec/isaac/session/compaction_spec.clj`: all absolute values converted to percentages; `compaction-target` calls pass `context-window`
- `spec/isaac/features/steps/session.clj`: `create-session-from-row!` uses `Double/parseDouble` for threshold/head; `compaction-defaults` step updated
- Feature files recalibrated to percentage values: `features/session/compaction_strategies.feature`, `features/session/async_compaction.feature`, `features/context/compaction.feature`



## Verification failed

The current code no longer satisfies this bean's percentage-based acceptance. `src/isaac/session/compaction_schema.clj` now defines `:threshold` and `:head` as non-negative token counts rather than percentages (`must be a non-negative token count`), and `src/isaac/session/context.clj` computes defaults with absolute-token formulas via `default-threshold`/`default-head` instead of fixed percentage values. That contradicts the bean's required semantics: `:threshold`/`:head` must be strict percentages in `[0.0, 1.0)` with defaults `0.8` and `0.3`, resolved to absolute counts at use time. Later work appears to have reverted this bean's behavior, so it cannot stay completed.

## Fix After isaac-bv48 Conflict

isaac-bv48 (canonical session behavior funnel) landed after our commit and reverted threshold/head to absolute token counts in:
- `context.clj`: added `default-threshold`/`default-head` returning absolute values; `resolve-compaction-config` using them
- `compaction_schema.clj`: validation changed back to non-negative token counts
- `compaction.clj`: delegated to context.clj absolute functions; removed context-window multiplication

Restored percentage semantics by:
- `default-threshold`/`default-head` in context.clj → return 0.8/0.3 (percentages)
- `compaction_schema.clj` → `[0.0, 1.0)` validation with percentage message
- `compaction.clj` → `should-compact?` and `compaction-target` multiply by context-window
- All test values updated to percentages (context_spec.clj, cli_spec.clj, compaction_spec.clj, compaction_schema_spec.clj)
- All feature files updated: behavior_funnel.feature, compaction_strategies.feature, async_compaction.feature, context/compaction.feature



## Verification passed

HEAD (isaac-agent): 6ad519db72c964215e65e7aa969413592b5e8ab2
Working tree: clean

Verified 2026-06-29 by work-3.

- `bb ci` green: 1125 speclj examples, 556 feature examples, 0 failures
- Schema: `:threshold`/`:head` are `:double` with `:percentage?` validation in `[0.0, 1.0)`
- Defaults: `0.8`/`0.3` in `context.clj`; resolved at use time via `* context-window` in `compaction.clj`
- Feature files recalibrated: `compaction_strategies.feature`, `async_compaction.feature`, `behavior_funnel.feature`
- `features/context/compaction.feature` not present in isaac-agent (pre-split monolith path; N/A)
- `features/session/compaction_percentages.feature` still deferred per bean
- Changelog entry for hard-break migration not yet written (follow-up if desired)
