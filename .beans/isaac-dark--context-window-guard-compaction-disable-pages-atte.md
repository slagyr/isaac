---
# isaac-dark
title: 'Context-window guard: compaction-disable pages attention; over-window turns defer as weather, never stream-death'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-07-09T16:13:11Z
updated_at: 2026-07-09T19:19:10Z
---

## Goal

A session that can no longer compact must not become a silent countdown timer. Two halves: **disabling compaction pages a human**, and **a turn that cannot fit the model's window fails before the request** as classified weather — never as provider stream-death burning dead-letter budget.

## Observed (2026-07-08/09, zanebot isaac-work-1)

Compaction failed repeatedly (a ~277K-token chunk could not fit any summarization request) → after max consecutive failures `turn.clj` stamped `:compaction-disabled true` — announced only via the comm callback, which on hail turns is the null comm. The session sat at ~196K tokens, harmless under gpt-5.4 (278K window), then scrapper moved to grok-composer (200K window): every stream died mid-generation ("responses stream ended without response.completed"), classified `:llm-error`, and two healthy hails (la8h handback, jx7u dispatch) burned 5 attempts each and dead-lettered in ~3 minutes. On the wire, a deterministic over-window stream death is indistinguishable from a transient blip — classification cannot fix this; only a local pre-request guard can.

## Design

1. **Attention on disable**: when consecutive compaction failures flip `:compaction-disabled`, post to `:attention :notify` (isaac-5a4n config, durable comm outbox): session, reason, total-tokens, window. The existing null-comm callback and WARN log stay.
2. **Pre-request window guard**: at turn start, if estimated total-tokens ≥ the model's context window (small safety margin, e.g. 98%) and compaction is disabled or has just failed, do NOT send the request. The turn result is classified weather: `{:unavailable? true :reason :context-exhausted :retry-after-ms <auth-tier default>}` — the hail worker defers (zero attempt burn, isaac-3tvq contract) and the deferral posts attention per 5a4n's system-scoped routing (throttled per session). Recovery is human/planner action (re-enable compaction, session surgery, model with a bigger window) — after which parked deliveries sail through, per the hails-never-die doctrine.
3. **Post-fix leftovers**: stream deaths that still occur are genuinely transient; `:llm-error` attempt-burning remains correct for them. No reclassification.

## Scenarios (worker writes; required coverage)

1. Compaction-failure cap reached: `:compaction-disabled` lands AND `comm/delivery/pending` gains exactly 1 file (attention post with session + reason). (Recording surface: the durable outbox, as in 5a4n S3.)
2. Turn on a session with compaction disabled and total-tokens over the guard line: no outbound LLM request is made (grover records zero requests); turn result unavailable with `:reason :context-exhausted`; hail delivery defers with attempts unchanged.
3. Same guard line but compaction ENABLED: compaction runs first, and when it succeeds the turn proceeds normally (guard only bites when compaction cannot save the turn).
4. Deferral attention throttled per session (second guarded turn within the window posts nothing new).

## Acceptance

- [ ] Scenarios green
- [ ] One-time: replay of the incident shape on zanebot — a wedged session defers with attention instead of dead-lettering its hails

## Verify fail (attempt 1, 2026-07-09): code/tests are green, but the required zanebot replay acceptance is still unmet

Evidence:
- I verified implementation in two repos:
  - `isaac-agent` branch `origin/bean/isaac-dark` at `c72a3f6`
  - `isaac-hail` branch `origin/bean/isaac-dark` at `31d833e`
- Bean-targeted checks are green:
  - `isaac-agent`: `bb features features/session/context_window_guard.feature` -> `3 examples, 0 failures, 12 assertions`
  - `isaac-agent`: `bb spec spec/isaac/attention_spec.clj spec/isaac/comm/comm_steps.clj spec/isaac/session/session_steps.clj` -> `22 examples, 0 failures, 57 assertions`
  - `isaac-hail`: `bb features features/context_window_guard.feature` -> `1 examples, 0 failures, 2 assertions`
  - `isaac-hail`: `bb spec spec/isaac/hail/delivery_worker_spec.clj` -> `21 examples, 0 failures, 53 assertions`
- Broader validation is also green:
  - `isaac-agent` `bb ci` -> `1198 examples, 0 failures, 2362 assertions`; features pass -> `613 examples, 0 failures, 1400 assertions`
  - `isaac-hail` `bb ci` -> `135 examples, 0 failures, 513 assertions, 2 pending`
- The code changes match the bean design: agent adds compaction-disable attention + pre-request context-window guard; hail adds context-exhausted attention/deferral handling.
- However, the bean Acceptance section still has an unchecked required item:
  - `- [ ] One-time: replay of the incident shape on zanebot — a wedged session defers with attention instead of dead-lettering its hails`
- I found no worker note, planner note, or bean update documenting that replay as completed or waived.
- Per verify policy, unmet acceptance means the bean cannot pass even with green code/test evidence.
