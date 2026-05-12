---
# isaac-efzy
title: "Normalize per-turn :usage block on assistant transcript entries"
status: todo
type: feature
priority: low
created_at: 2026-05-11T20:37:39Z
updated_at: 2026-05-11T23:24:04Z
---

## Description

The :usage block written by store-response! (src/isaac/drive/turn.clj:162)
is partial and divergent from the rest of the codebase:

- Missing :cache-write at the per-turn level (only tracked at session
  aggregate)
- Missing :total-tokens rollup inside :usage
- Absent entirely when the provider returns no usage (Ollama path)
- The :tokens int (loop-accumulated) and :usage map (last-response
  only) read from different sources, so they can disagree
- Internal :cached-tokens diverges from :cache-read used everywhere else

This bead makes every assistant transcript entry carry one consistent
:usage block, sourced from the tool-loop accumulator, with stable
field names.

Spec: features/session/turn_usage.feature (six scenarios, @wip)
Run:  bb gherclj features/session/turn_usage.feature

## Acceptance Criteria

- @wip tag removed from features/session/turn_usage.feature
- All six scenarios in features/session/turn_usage.feature pass
- bb features still green (no regressions, especially storage.feature
  and llm_interaction.feature)
- normalize-usage emits {:input-tokens, :output-tokens, :total-tokens,
  :cache-read, :cache-write, [:reasoning-tokens]} consistently

## Design

- normalize-usage (src/isaac/drive/turn.clj:56) always produces a map:
  {:input-tokens N :output-tokens N :total-tokens N
   :cache-read N :cache-write N :reasoning-tokens? N}
  Reasoning-tokens stays optional; the rest default to 0.

- Per-turn :usage sourced from the tool-loop accumulator (extract-tokens
  result), not the last response's :usage block, so accumulation works
  across tool-loop iterations.

- Rename internal :cached-tokens -> :cache-read to match
  llm/api/anthropic_messages.clj and tool_loop.clj.

- queued-response-headers in spec/isaac/features/steps/session.clj:225
  must accept usage.cache_creation_input_tokens for the cache-write
  scenario.

## Notes

Verification failed: bb spec passed, but the bead's required feature check failed. bb gherclj features/session/turn_usage.feature reported 5 failing scenarios, including missing message.usage.total-tokens, message.usage.cache-read, message.usage.cache-write, zero-default usage fields, and tool-loop accumulation/content mismatches.

