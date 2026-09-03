---
# isaac-vuto
title: 'Token gauge is blind mid-turn: last-input-tokens is stamped once per turn, so a long tool-loop turn compacts off the undercounting estimate'
status: in-progress
type: bug
priority: high
tags:
    - compaction
    - token-accounting
created_at: 2026-09-03T20:36:03Z
updated_at: 2026-09-03T20:42:43Z
---

Observed 2026-09-03 on zanebot (agent 0.1.41 / 13da406), session isaac-work-2 (scrapper, grok-4.6, window 500K, threshold 0.8): a fresh session ran ONE tool-loop turn from 17:49 to 18:47 (isaac-jarr work). Every mid-turn `:session/compaction-check` logged `:total-tokens ~304K` (61%) and never compacted; at turn end `:session/token-drift` fired and the provider stamp landed at **455,183** (91%). The session sat at 91% with 0 compactions.

Why (isaac-agent main, verified in source):
- `run-compaction-check!` (drive/turn.clj) computes `total-tokens` = `compaction/estimate-prompt-tokens` (a rebuild-and-count estimate) and hands it to `should-compact?`, whose `context-gauge` is `(max estimate (:last-input-tokens session-entry))` — correct design (pqjn/x2up).
- BUT `:last-input-tokens` is written only by `store-response!`, which `execute-llm-turn!` runs once for the FINAL assistant response of a turn ("Tool pairs are written mid-loop by record-tool-call!" — usage is not). Cycle 2..N provider responses carry `prompt_tokens` that never reach the store.
- So inside a turn the gauge's second leg is the PREVIOUS turn's stamp (0 for a fresh/archived session). The estimate undercounts grok prompts by ~33% (drift ratio ≈1.5 on this session; see isaac-1umd for the cycle-2+ body-size question), so a long turn sails past the threshold blind. The p9zy overflow compact-and-retry only fires when the provider actually rejects (500K wall), which is 100K past the threshold.

Expected: every provider response's `prompt_tokens` (each cycle, not just the last) updates `:last-input-tokens` before the next mid-turn compaction check, so the gauge tracks the real prompt within one cycle. Keep the estimate as the floor for the first cycle.

Runnable acceptance to write (@wip, features/session/compaction or token_accounting.feature): (1) a tool-loop turn whose cycle-2 provider usage reports prompt_tokens above threshold triggers mid-turn compaction on the next batch even though the estimate is below threshold; (2) the session entry's last-input-tokens after cycle 2 equals cycle 2's prompt_tokens (not the turn-final value); (3) existing pqjn stamp/drift scenarios stay green. Related: isaac-pqjn, isaac-p9zy, isaac-x2up, isaac-1umd, isaac-dgod (12M gauge on orchestration-verify — check whether the same per-turn stamping path is summing instead of replacing on a stateful Responses chain).



## Decisions (2026-09-03, Micah: "fix the token accounting once and for all")

1. **Stamp per cycle.** The tool loop's `on-cycle` hook (drive/turn.clj ~1177) already sees every cycle's response at phase :end. Stamp `:last-input-tokens` with that response's prompt count there (positive values only), so the next mid-turn `run-compaction-check!` gauge reads the real prompt within one cycle. `store-response!` at turn end keeps its stamp (it becomes a no-op refresh).
2. **Last, never sum.** `:last-input-tokens` is the most recent cycle's prompt count; `:turn-input-tokens` stays the per-turn sum; `:input-tokens` stays cumulative. Guard the sidecar fallback (`update-tokens!`: `last-input-tokens (or … input-tokens)`) so a caller that passes only the cumulative/sum can never land it in the stamp.
3. **Calibrate the estimate.** Persist the last observed drift ratio (provider ÷ stamped, already computed by `log-token-drift!`) on the session entry (`:token-drift-ratio`, system-managed, schema); `context-gauge` becomes `max(estimate × ratio, stamp)` with ratio defaulting to 1.0 and clamped to [1.0, 3.0]. `:session/compaction-check` logs `:gauge` and `:ratio` alongside `:total-tokens`. This closes the cycle-1 blind spot on a fresh/archived session, where no stamp exists yet.
4. **Investigate the 12M stamp** on zanebot `orchestration-verify` (last-input-tokens 12,031,158 > window; isaac-dgod). Read its transcript's assistant `:usage` entries and the sidecar history to find which write produced it; if it is a sum reaching the stamp, decision 2 fixes it — add the reproducing scenario; if it is provider-reported (Responses stateful chain), cap the stamp at context-window and log `:session/stamp-implausible`. Record the finding on this bean and isaac-dgod.

## Features (`@wip`) — isaac-agent `features/session/token_accounting.feature` @ 729cc04

1. a mid-turn provider count over the threshold compacts before the next cycle
2. the provider stamp is the last cycle's prompt count, never the sum of cycles
3. the gauge is calibrated by the last observed drift ratio

Existing rows in token_accounting.feature and compaction_overflow.feature are the regression net and must stay green.

## Step ledger

| step | status |
|------|--------|
| isaac EDN file exists with / built-in tools registered / crew allows tools / sessions exist / session has transcript (tokens) / model responses queued (usage.input_tokens, tool_call rows) / user sends / transcript matching / log has entries matching | reuse |
| the following sessions match: (columns last-input-tokens, turn-input-tokens) | reuse — confirm the step reads sidecar token fields; if it only reads names, extend it (fixture-side helper, not a new phrase) |
| `:session/compaction-check` log keys `gauge`, `ratio` | product change (decision 3), asserted through the existing log step |

Fixture note: scenario 1's queued tool_call row carries `usage.input_tokens 850` on the FIRST cycle; the compaction summary response is consumed by the mid-turn compaction, then the final text. If grover's scripted-response table does not accept usage on tool_call rows, extend grover's fixture reader (test seam), never the scenario.

## Acceptance

    cd isaac-agent
    bb features features/session/token_accounting.feature
    bb features features/session/compaction_overflow.feature features/session/compaction_logging.feature
    bb spec spec/isaac/session spec/isaac/drive
    bb features && bb spec    # full gate, exit codes
Remove @wip when green; note decision-4 finding on this bean. Evidence from the field (2026-09-03): isaac-work-2 ran one 58-minute tool-loop turn blind at ~304K estimate while the provider reported 455K; on the next turn (0.1.41) it compacted itself 455K→7.5K, so the turn-boundary path works — only the mid-turn path is blind.
