---
# isaac-dark
title: 'Context-window guard: compaction-disable pages attention; over-window turns defer as weather, never stream-death'
status: in-progress
type: feature
priority: high
created_at: 2026-07-09T16:13:11Z
updated_at: 2026-07-09T18:21:35Z
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
