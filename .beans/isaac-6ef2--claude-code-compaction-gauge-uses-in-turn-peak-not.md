---
# isaac-6ef2
title: claude-code gauge reads the result's turn-total usage as context size
status: todo
type: bug
priority: high
created_at: 2026-09-29T14:31:12Z
updated_at: 2026-09-29T14:54:34Z
---

Ruling direction: Micah, 2026-09-29. The compaction gauge on the claude-code provider uses the wrong number to mean "context size."

## Problem

On a stateless provider, `:last-input-tokens` is supposed to predict the next prompt. For claude-code, each Isaac turn runs Claude's own tool loop inside the CLI (7 to 136 provider cycles). The stored figure is the turn's **largest cycle** (`turn/request-measured :provider-cycle-max`), built by `parse-cli-usage` in `isaac-claude-code` as input + cache-read + cache-write. That peak includes tool results piled up inside the CLI's loop. Those results never reach Isaac's transcript, and the next turn starts again from ~80k (Isaac's ~30k plus the CLI's ~55k base).

The figure can't be a context size anyway. Yopp 2026-09-28 17:21Z: 136 cycles, cycle max **4,757,749** on a 1,000,000 window. So the CLI's per-cycle usage is itself something else (cumulative across the turn, subagent roll-up, or similar). Nobody has captured the raw usage objects that would tell us which.

Yopp session `acp-2026-09-28-1645-a6c4`, every compaction was pointless:
- 09-28 17:25Z: gauge 4,793,047, transcript 63,819 tokens → compacted (3m48s).
- 09-29 13:51Z: gauge 930,470, transcript 20,533 → compacted (5m48s).
- 09-29 14:04Z: turn cycle max 809,751 on a ~35k-token request, so the **next** turn compacts again.

## Wanted

1. Find out what the CLI's usage numbers actually are. Capture the raw stream-json `usage` for a multi-cycle turn (per assistant message and the final `result`). Write down which figure, if any, is "prompt size of one API request."
2. Set the gauge from the figure that predicts the *next* request Isaac will send: the prompt size of the turn's **first** cycle (the request Isaac composed), or Isaac's own estimate plus measured CLI overhead. Never the in-turn peak, never a sum.
3. Keep per-turn spend (summed prompt / cache tokens) as accounting only. It never feeds `should-compact?`.

## Acceptance

- Scenario: claude-code turn with cycles 80k → 600k → gauge after the turn ≈ 80k (first cycle), not 600k; the next turn does not compact.
- Scenario: a cycle figure larger than the context window is never stored as the gauge.
- Scenario: a real transcript over threshold still compacts (regression guard).
- Scenarios + `bb bean-gate baseline` before todo. Step 1's findings go in this bean body first. They may change step 2.

## Red first (planner, 2026-09-29)

The scenario passes against today's driver, because the fake CLI (`cycle-block-events` in `src/isaac/llm/api/claude_cli.clj`) ignores the `result_usage` row and copies the last cycle's usage onto the result event. The real CLI never does that. First teach the fake the real shape: each cycle's `usage` rides its own assistant message, the result carries `result_usage`, and with no `result_usage` row the result carries the sum of the cycles. Then confirm the scenario fails (`last-input-tokens` 92754) before fixing the driver.

## Likely repo scope

`isaac-claude-code` (usage parsing / which cycle stamps; see isaac-8cur), `isaac-agent` (`drive/accounting.clj`, the gauge stamp).

## Related

Slow compaction: the summary call through the claude-code CLI took 4m16s for a 22k-token prompt. Out of scope here; worth its own look.

feature-baseline: isaac-claude-code cc21c0fd598cc212e0ea02a14e2ee17c34157719
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 4d74b229e357d0dd2de862283e8824a68660820f 561
