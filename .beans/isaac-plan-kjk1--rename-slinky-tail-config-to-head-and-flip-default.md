---
# isaac-plan-kjk1
title: Rename slinky :tail config to :head and flip default to 30% of window
status: completed
type: task
priority: normal
created_at: 2026-05-12T22:10:47Z
updated_at: 2026-05-12T22:14:40Z
---

The slinky compaction param :tail is named backwards from the metaphor —
in the slinky toy, the head is the recent/preserved end and the tail is
what trails behind and gets folded. The current code computes
default-tail = ~70% of window and the loop preserves that amount as
the recent head, so the param name and the code are out of sync.

## Changes

- Rename :tail → :head in compaction config schema (compaction.clj, schema.clj).
- Rename default-tail → default-head.
- Flip the formula: default-head = int(0.3 × window) so a head is a head —
  small recent chunk preserved, large older chunk folded. Drops the
  150K-overhead-subtraction term entirely; for tiny test windows it
  just gives 30% of window.
- Rename slinky loop's `preserved` accumulator to `head` for clarity.
- Rename the `:tail-threshold` cross-field constraint to `:head-threshold`.
- Update spec/isaac/session/compaction_spec.clj defaults (5 rows of test data).
- Update spec/isaac/bridge/chat_cli_spec.clj two scenarios that pass :tail.
- Update spec/isaac/features/steps/session.clj table column "compaction.tail" → "compaction.head".
- Update features/session/compaction_strategies.feature — defaults table and scenario.

## Migration note

No alias for the old key. Existing session records on disk with :tail set
will fall back to the new default-head (~30% of window) on next read,
since resolve-config select-keys only for the new key.

## Verification

- bb spec — green
- bb features features/session/compaction_strategies.feature — green

## Summary of Changes

- `src/isaac/session/compaction.clj`: renamed schema key `:tail` → `:head`,
  function `default-tail` → `default-head` (formula = 0.3 × window, drops the
  150K-overhead-subtraction term), constraint `:tail-threshold` →
  `:head-threshold`, slinky loop variable `preserved` → `head-size`.
- `src/isaac/session/schema.clj`: CompactionState schema `:tail` → `:head`.
- `spec/isaac/session/compaction_spec.clj`: default-tail tests updated to
  default-head with new 30% expectations.
- `spec/isaac/bridge/chat_cli_spec.clj`: two async-compaction scenarios.
- `spec/isaac/features/steps/session.clj`: session-row table column
  `compaction.tail` → `compaction.head`, and the defaults-table assertion.
- 3 feature files: `features/session/compaction_strategies.feature`,
  `features/session/async_compaction.feature`,
  `features/context/compaction.feature`.

bb spec: 1573 examples, 0 failures.
bb features: 584 examples, 0 failures.

## Deploy note

Existing session records with `:tail N` on disk will silently fall back to
the new `default-head` on next read (resolve-config select-keys only for
the new key). For Marvin on zanebot, the session has no explicit value, so
it just picks up the new 30% default once Isaac is rebuilt.
