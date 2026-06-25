---
# isaac-3eqp
title: Anthropic max_tokens must exceed thinking budget_tokens (default effort 7 breaks claude turns)
status: todo
type: bug
priority: high
created_at: 2026-06-25T15:28:39Z
updated_at: 2026-06-25T15:28:39Z
---

Anthropic Messages requests can send a thinking `budget_tokens` larger than `max_tokens`, which the API rejects: `max_tokens must be greater than thinking.budget_tokens`. Any fresh claude session that doesn't override effort hits this and CANNOT take a turn.

## Mechanism (isaac-agent, src/isaac/llm/api/messages.clj)
- `request-body` hardcodes `:max_tokens` default **4096** (`:or {max-tokens 4096}`); nothing in the config chain sets it (model-cfg only feeds :thinking-budget-max/:enforce-context-window/:think-mode — never max-tokens).
- `effort->thinking`: `budget_tokens = (int (* effort (/ (or budget-max 32000) 10)))`.
- `default-effort` is **7** (isaac.effort, since 2026-06-14). Resolution chain: session > crew > model > provider > defaults > 7.
- So a turn with no explicit effort -> effort 7, budget-max 32000 -> budget_tokens = 22400 > max_tokens 4096 -> API 400.

## Why it's latent / surfaced now
Existing sessions had `:effort 0` set (no thinking) so they worked; a FRESH claude session falls through to default 7 and breaks. `isaac prompt` (CLI) doesn't resolve effort -> no thinking block -> appears fine. So it specifically hits new claude sessions (Discord/iMessage/remote). Pre-existing; not introduced by a recent deploy (messages.clj/effort.clj unchanged across recent agent shas).

## Core problem
max_tokens (the Anthropic field) is the TOTAL cap and must exceed budget_tokens; response tokens = max_tokens - budget_tokens. A fixed 4096 can't coexist with any meaningful thinking budget (even effort 1 -> budget 3200 leaves ~900 response tokens). max_tokens must scale with the thinking budget.

## Fix (design approved 2026-06-25, Micah)
The messages adapter must emit TWO numbers — thinking budget AND max_tokens — modeled as separable quantities so `max_tokens > budget_tokens` holds by construction:

1. **Keep the existing effort->budget curve** (unchanged; existing api.feature scenarios stay green):
   `budget_tokens = round(effort/10 * thinking-budget-max)` (per-model `:thinking-budget-max`, default 32000). effort 0 omits the thinking block.
   - effort 1 -> 3200, 5 -> 16000, 10 -> 32000 (with default budget-max).
   - **Clamp to Anthropic's 1024 floor** when effort > 0 (so a small budget-max can't produce an illegal sub-1024 budget — a latent bug today).
2. **Add a response budget and compute max_tokens:**
   `max_tokens = budget_tokens + response_budget`, where `response_budget` is a configurable model field `:max-output-tokens` (add to resolve.clj select-keys), default **4096** (keeps today's non-thinking behavior identical).
   - effort 0 / no thinking -> `max_tokens = response_budget`.
   - **Clamp max_tokens to the model's output ceiling** (configurable, sane default) so a huge budget-max can't exceed what the model allows.

Decision: KEEP the 0-10 curve and per-model budget-max; only ADD the max_tokens computation. (Rejected: re-anchoring the budget as a fraction of max_tokens — guarantees the invariant too but changes every existing budget number and breaks current scenarios for no gain.)

## Scenarios
Under review with Micah (one at a time) — to land in `features/llm/api/messages/api.feature` (@wip), reusing existing steps (`the last outbound HTTP request matches:`, `the last provider request does not contain path`, config/session/user-sends — NO new steps). Recorded here as each is approved.

## Acceptance
- For any effort/budget-max, the Anthropic request has max_tokens > thinking.budget_tokens (no 400).
- A fresh claude session (default effort 7) takes a turn successfully end-to-end.
- effort 0 -> no thinking block; max_tokens = response budget.
- Response budget configurable (`:max-output-tokens`), sensible default; non-thinking behavior unchanged.
- api.feature scenarios added (@wip) and green; effort.feature unaffected.

## Notes
Surfaced 2026-06-25 on zanebot: fresh claude session failed every turn with `invalid_request_error: max_tokens must be greater than thinking.budget_tokens`. Micah declined the effort-0 config mitigation; fix it properly in the adapter. Consider whether default-effort 7 is also too aggressive, but the real bug is max_tokens not scaling.
