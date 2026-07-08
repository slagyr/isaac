---
# isaac-7l5m
title: 'Responses API chaining: previous_response_id within tool-loop turns (provider-gated)'
status: draft
type: feature
priority: normal
created_at: 2026-07-08T20:46:12Z
updated_at: 2026-07-08T20:46:12Z
---

## Goal

Use the Responses API as designed on providers that support it: within a tool-loop turn, chain requests with `previous_response_id` + `store: true` instead of resending the whole context every cycle. wpny's work turns sent ~1.1MB bodies × ~80 cycles per turn; chained, cycle 2..N sends only the new tool results.

## Probe evidence (2026-07-08, live)

- **xAI (api.x.ai/v1/responses, subscription token): chaining works.** r1 `store:true` → r2 sent only the follow-up + `previous_response_id`, model answered from server-side state (561 input tokens vs a resent transcript).
- **chatgpt codex backend: hard-rejects statefulness** — `400 {"detail":"Store must be set to false"}`. Chaining is impossible there; capability must be per-provider.
- Isaac today: `responses.clj:48` hard-codes `:store false`, no `previous_response_id` anywhere.

## Design

- **Scope: within-turn only.** Each turn's first request sends the full context exactly as today (fresh chain); tool-loop cycles 2..N send `{:input <tool results only> :previous_response_id <last completed response id> :store true}`. Turn boundaries, compaction, suspend/resume, and transcript-as-truth are all untouched — the transcript is still written locally from streamed events; the chain is a transport optimization inside one turn.
- **Provider capability flag**: `:response-chaining true` on the provider config/template (set it on :grok / :xai templates). Default false; chatgpt stays false (backend policy). Non-Responses APIs ignore it.
- **Chain from the last COMPLETED response id only** (captured from the stream's `response.created`/`completed` events). A failed/aborted cycle re-chains from the prior good id.
- **Self-healing fallback**: on a `previous_response_id ... not found` error (server-side expiry/eviction — xAI retention is undocumented), transparently retry that cycle with full context and start a new chain. Log `:chat/chain-reset` (info).
- **Token accounting unchanged**: the server still bills stored context as input tokens (561 above included the chained history), so this saves upload bandwidth, client prompt-build time, and TTFT — not tokens. On subscription providers tokens are flat-rate anyway.

## Note for Micah (accepted trade-off)

`store: true` means xAI retains the conversation server-side for its retention window (undocumented length). Inputs already transit xAI regardless; this adds persistence. Called out so it's a decision, not a surprise.

## Scenarios (worker writes; required coverage — grover needs chaining support first)

1. Chained turn: with `:response-chaining true`, cycle 1 sends full context and no `previous_response_id`; cycle 2 carries `previous_response_id` = cycle 1's response id, `store: true`, and input containing ONLY the new tool results (assert absence of the original history from the body).
2. Capability off (default / chatgpt): every cycle sends full context, `store: false` — behavior identical to today.
3. Chain reset: cycle N returns a previous-response-not-found error; the cycle retries once with full context, a new chain starts, the turn completes, `:chat/chain-reset` logged.
4. Turn boundary: a new turn on the same session starts with full context (no id carried across turns).

Grover: scripted responses gain response ids; a scripted `previous-response-not-found` error type; request assertions can inspect `:previous_response_id` and the input contents.

## Acceptance

- [ ] Scenarios green in isaac-agent
- [ ] One-time on zanebot: a grok-composer work turn's cycle-2+ request bodies drop from ~1MB to KB-scale (verify via `:llm/http-request :body-chars` in server.log)
- [ ] chatgpt turns byte-identical in behavior (store:false, no chaining fields)
