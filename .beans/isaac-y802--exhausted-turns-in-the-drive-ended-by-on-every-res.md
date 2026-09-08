---
# isaac-y802
title: 'Exhausted turns in the drive: :ended-by on every result, Comm on-exhausted policy, :cycle-limit (rename, default 100, charge override)'
status: todo
type: feature
priority: high
created_at: 2026-09-08T15:28:25Z
updated_at: 2026-09-08T15:28:25Z
parent: isaac-ntt6
---

Repo: isaac-agent. Child of isaac-ntt6 (decisions 1–4, 6). Feature: `features/llm/turn_exhaustion.feature` (5 @wip scenarios, planted on main affb005) + `features/tool/tool_loop_limit.feature` (rewritten @wip: :cycle-limit).

## Contract
- Every turn result carries `:ended-by` ∈ #{:reply :cycle-limit :cancelled :error :context-exhausted}; the drive logs `:turn/ended :session :ended-by [:exhaustion] [:error] [:cycle-limit]` at :info once per turn and passes the result (with :ended-by) to `comm/on-turn-end`. The existing `:ended-by :tool-loop-limit` becomes `:cycle-limit` (clean cutover).
- `isaac.comm.protocol/Comm` gains `(on-exhausted [comm session-key info])` → :stop | :wrap-up; `comm/defaults` = :stop. The tool loop / drive invokes it when the budget runs out with tools pending. :stop = today's behaviour (summary cycle with `:tools []`, canned fallback). :wrap-up = one final cycle WITH the turn's tools and a system nudge (budget exhausted; start nothing new; commit + push to the bean branch; write the done/next note; hand off if acceptance is met), then one tool-less note cycle; result gets `:exhaustion :wrapped-up`.
- An exhausted turn whose final content is empty (after :stop's summary+canned path for attended origins this cannot happen; after :wrap-up's note cycle it can) FAILS with `:error :empty-terminal-response` — never a success (closes the isaac-k4mf bypass).
- `:tool-loop-max` → `:cycle-limit` everywhere (config crew/defaults, resolver, tool_loop opts stay :max-loops internally is fine); built-in default 500 → 100. Resolution: charge `:cycle-limit` override → crew → defaults → built-in. Charge override is a unit spec (bridge/charge precedence), behavioural scenario lives in the hail child.
- memory comm (spec support) gains a knob so a feature can set its on-exhausted answer: step `Given the memory comm answers :wrap-up on exhaustion` (NEW).

## Step ledger
| Step | Status |
|---|---|
| the user sends … via memory comm / the memory comm has events matching: (with `result.ended-by`, `result.exhaustion`, `result.error` dotted columns) | existing |
| the log has entries matching: / the last LLM request matches: / session has transcript matching: / the following model responses are queued: / the turn is cancelled on session … after N tool call / a blocking tool … is registered … | existing |
| **Given the memory comm answers {policy:keyword} on exhaustion** | **NEW** — the only new step; sets the memory comm's on-exhausted reply |

## Acceptance
- `bb features features/llm/turn_exhaustion.feature` → 5 green with @wip removed
- `bb features features/tool/tool_loop_limit.feature` → green with @wip removed (:cycle-limit)
- `bb features && bb spec` green; no `tool-loop-max` left in src/spec/features (`grep -rn tool-loop-max` empty)
- Existing driver/cancel/compaction scenarios unchanged
- Deploy note: zanebot crews using tool-loop-max (scrapper 400) are re-keyed to `:cycle-limit 120` by the train (config edit, hot reload)
