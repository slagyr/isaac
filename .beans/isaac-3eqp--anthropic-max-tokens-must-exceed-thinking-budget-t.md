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

## Proposed fix
Make max_tokens = thinking budget_tokens + a RESPONSE budget, so it always exceeds budget_tokens with predictable response room:
- Add a configurable response budget, model field `:max-output-tokens` (resolve.clj select-keys), default **4096** (keeps today's non-thinking behavior identical).
- In request-body / send paths: `max_tokens = (+ budget_tokens max-output-tokens)` when thinking is enabled; `max_tokens = max-output-tokens` when effort 0 / no thinking.
- Guarantee invariant `max_tokens > budget_tokens` always.
(Minimal alternative: `max_tokens = budget_tokens + 4096` with no new config. Either is acceptable; the configurable version is preferred.)

## Scenarios (DRAFT — add to features/llm/api/messages/api.feature, @wip; reuse existing steps `the last outbound HTTP request matches:`, `the last provider request does not contain path`, config/session/user-sends. NO new steps.)
Match the file's existing background (grover-simulated anthropic, session "thinking", model with :thinking-budget-max).

```gherkin
@wip
Scenario: max_tokens exceeds the thinking budget so the request is valid
  # model :thinking-budget-max 32000, session/crew effort 7 -> budget 22400
  When the user sends "hi" on session "thinking"
  Then the last outbound HTTP request matches:
    | path                        | value |
    | body.thinking.budget_tokens | 22400 |
    | body.max_tokens             | 26496 |   # 22400 + 4096 response budget; > budget_tokens

@wip
Scenario: effort 0 sends only the response budget and no thinking
  # effort 0
  When the user sends "hi" on session "thinking"
  Then the last outbound HTTP request matches:
    | path            | value |
    | body.max_tokens | 4096  |
  And the last provider request does not contain path "body.thinking"

@wip
Scenario: response budget is configurable via :max-output-tokens
  # model :max-output-tokens 8192, :thinking-budget-max 32000, effort 5 -> budget 16000
  When the user sends "hi" on session "thinking"
  Then the last outbound HTTP request matches:
    | path            | value |
    | body.max_tokens | 24192 |   # 16000 + 8192
```
(Exact max_tokens numbers depend on the chosen response-budget default of 4096 — adjust if a different default is picked.)

## Acceptance
- For any effort/budget-max, the Anthropic request has max_tokens > thinking.budget_tokens (no 400).
- A fresh claude session (default effort 7) takes a turn successfully end-to-end.
- effort 0 -> no thinking block; max_tokens = response budget.
- Response budget configurable (`:max-output-tokens`), sensible default; non-thinking behavior unchanged.
- api.feature scenarios added (@wip) and green; effort.feature unaffected.

## Notes
Surfaced 2026-06-25 on zanebot: fresh claude session failed every turn with `invalid_request_error: max_tokens must be greater than thinking.budget_tokens`. Micah declined the effort-0 config mitigation; fix it properly in the adapter. Consider whether default-effort 7 is also too aggressive, but the real bug is max_tokens not scaling.
