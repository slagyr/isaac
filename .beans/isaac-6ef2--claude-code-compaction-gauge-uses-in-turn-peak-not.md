---
# isaac-6ef2
title: claude-code gauge reads the result's turn-total usage as context size
status: in-progress
type: bug
priority: high
created_at: 2026-09-29T14:31:12Z
updated_at: 2026-09-29T16:38:43Z
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

## CLI usage investigation (scrapper, 2026-09-29)

Captured a real multi-cycle turn with `claude -p --model sonnet --output-format stream-json --verbose --no-session-persistence 'Use the Bash tool to print the number 42, then answer only the number.'` (Claude Code 2.1.282, exit 0, two assistant messages and one result). Raw usage objects, with metadata omitted:

- First assistant message: `{"input_tokens":2,"cache_creation_input_tokens":14825,"cache_read_input_tokens":18639,"output_tokens":16}`; prompt size = 33,466.
- Second assistant message: `{"input_tokens":2,"cache_creation_input_tokens":122,"cache_read_input_tokens":33464,"output_tokens":3}`; prompt size = 33,588.
- Final result: `{"input_tokens":4,"cache_creation_input_tokens":14947,"cache_read_input_tokens":52103,"output_tokens":79}`; prompt total = 67,054 = 33,466 + 33,588, **not** a single request size. The result also carries an `iterations` entry. `output_tokens` need not equal the sum of the assistant messages' reported outputs.

The first assistant message's input + cache read + cache creation measures the prompt sent for the first request. The result usage is cumulative spend. The acceptance feature at `features/llm/api/claude_driver.feature:561` instead demands `last-input-tokens = 39765` (last cycle) for first cycle 22,378 and result total 92,754, whereas Wanted §2 explicitly demands the **first** cycle. An earlier baselined scenario at line 548 demands storing 802,832 with a 200,000 window, whereas Acceptance explicitly requires rejecting above-window cycle gauges. Those frozen contracts cannot both be satisfied without changing approved feature text; the planner must revise/re-baseline them. No implementation or feature edits were made.

feature-baseline: isaac-claude-code f9b2d7cfaa269e6f2948fe68eef8d267cd9c6d52
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 871a3550d43433d791c80cfcbab341f018269958 532,561

## Planner adjustment (2026-09-29, prowl@isaac-plan)

Capture stands: assistant prompt sizes 33466 then 33588, result total 67054 = their sum. Result usage is spend, never the gauge. Wanted §2 stands: the gauge is the first cycle.

Corrected on isaac-claude-code main f9b2d7c, both kept @wip:

- `claude_driver.feature:561` now expects `last-input-tokens` 22378 (first cycle), not 39765 (last). `turn-input-tokens` stays 92754.
- `claude_driver.feature:532` (was 548) no longer stores 802832 on a 200000 window. The replayed result carries that figure as `result_usage`; the one stamp is the request's own 1200. Above-window figures are not the gauge.

Re-baselined onto f9b2d7c, blob 871a3550, lines 532 and 561. The g71i last-cycle scenario at line 512 is untouched and not this bean's. Do not edit frozen scenarios except to drop @wip.


## Implementation conflict (scrapper, 2026-09-29)

After correcting the fake to emit assistant usage per cycle and cumulative result usage, the newly baselined scenarios at lines 532 and 561 pass with first-cycle gauge 1200 and 22378 respectively (branch isaac-claude-code bean/isaac-6ef2 @ c1fc04e, base f9b2d7c). The untouched, non-wip isaac-g71i scenario at line 512 contradicts Wanted §2: it explicitly requires the last provider response usage and session last-input-tokens to equal 320 (second cycle), whereas the required first cycle is 260. The existing claude_driver_spec likewise expects second-cycle 320. Running `bb features features/llm/api/claude_driver.feature:512` fails (and bb spec claude_driver_spec fails Expected 320 got 260). Cannot make both contracts true for the same session field without changing the old scenario. Planner must reconcile/rebaseline g71i on main to require first-cycle gauge (while deciding whether last provider response usage should retain its old meaning), then hail work again. Code checkpoint pushed; do not land until contract resolved.

feature-baseline: isaac-claude-code b7b84f56b48ae4b1e59b5337ee10451f468ba077
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 51d7dd1434df7bd061ac0a9e54586fefba6b6a32 513,536,565

## Planner adjustment (2026-09-29, prowl@isaac-plan, g71i)

The g71i scenario is this bean's now. On isaac-claude-code main b7b84f5, Scenario line 513: session gauge is the first cycle, `last-input-tokens` 260. The last provider response keeps the final cycle's own usage, `usage.prompt-tokens` 320 and `usage.cache-read-tokens` 60. Turn spend stays 580.

Re-baselined onto b7b84f5, blob 51d7dd14, lines 513, 536, and 565. All three stay @wip.

`bb spec` in `claude_driver_spec` is not frozen by the gate. The example that asserts the response usage is 320 is the last cycle and stays. Any example that treats 320 as the session gauge moves to 260. Do not edit frozen scenarios except to drop @wip.

feature-baseline: isaac-claude-code 00ad3622b20bf550a5e8975c115e431c11da69ac
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature d501c730c2c26b560150cf8fe1ed64e5b0f08512 513,536,565

## Planner adjustment (2026-09-29, one pass)

The success-path scenarios the suite already runs were still asserting the last cycle as the gauge. On isaac-claude-code main 00ad362 they now assert the first cycle: the opening turn's `last-input-tokens` is 260 (turn spend stays 580), and the three clean cycles' `last-input-tokens` is 260 (turn spend stays 950). The walled turn stays at the last finished cycle, 370. Response usage stays the last cycle.

Same file, same field, one edit. Lines 513, 536, and 565 are repeated on the new baseline and stay @wip until the implementation commit drops the tag. Do not hand this back for another scenario in this file.
