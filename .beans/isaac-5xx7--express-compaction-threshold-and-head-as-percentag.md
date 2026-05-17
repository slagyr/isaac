---
# isaac-5xx7
title: Express compaction :threshold and :head as percentages of context-window
status: in-progress
type: feature
priority: normal
created_at: 2026-05-16T23:56:24Z
updated_at: 2026-05-17T20:03:38Z
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
