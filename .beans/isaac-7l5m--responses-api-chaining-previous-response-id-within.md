---
# isaac-7l5m
title: 'Stateful Responses API conversations: previous_response_id within tool-loop turns (provider/model-gated)'
status: draft
type: feature
priority: normal
created_at: 2026-07-08T20:46:12Z
updated_at: 2026-07-08T21:24:32Z
---

## Goal

Use the Responses API as designed on providers that support it: within a tool-loop turn, chain requests with `previous_response_id` + `store: true` instead of resending the whole context every cycle. wpny's work turns sent ~1.1MB bodies × ~80 cycles per turn; chained, cycle 2..N sends only the new tool results.

## Probe evidence (2026-07-08, live)

- **xAI (api.x.ai/v1/responses, subscription token): chaining works.** r1 `store:true` → r2 sent only the follow-up + `previous_response_id`, model answered from server-side state (561 input tokens vs a resent transcript).
- **chatgpt codex backend: statefulness disabled entirely** — `store:true` → `400 "Store must be set to false"`; `previous_response_id` (even with store:false) → `400 "Unsupported parameter: previous_response_id"`. The subscription funnel is a deliberately stateless proxy; OpenAI's platform API (api.openai.com, API-key) supports chaining fully — relevant only if an openai API-key provider is ever added.
- Isaac today: `responses.clj:48` hard-codes `:store false`, no `previous_response_id` anywhere.

## Design

- **Scope: within-turn only.** Each turn's first request sends the full context exactly as today (fresh chain); tool-loop cycles 2..N send `{:input <tool results only> :previous_response_id <last completed response id> :store true}`. Turn boundaries, compaction, suspend/resume, and transcript-as-truth are all untouched — the transcript is still written locally from streamed events; the chain is a transport optimization inside one turn.
- **Capability flag resolves through the standard config layering** (provider template < model file < crew < session), so it is per-model gateable per Micah: `:stateful true` on the :grok / :xai provider templates; any model file can override it off (or on, e.g. a future openai API-key provider). Default false; chatgpt stays false (backend disables statefulness). Non-Responses APIs ignore it.
- **Chain from the last COMPLETED response id only** (captured from the stream's `response.created`/`completed` events). A failed/aborted cycle re-chains from the prior good id.
- **Self-healing fallback**: on a `previous_response_id ... not found` error (server-side expiry/eviction — xAI retention is undocumented), transparently retry that cycle with full context and start a new chain. Log `:chat/chain-reset` (info).
- **Token accounting unchanged**: the server still bills stored context as input tokens (561 above included the chained history), so this saves upload bandwidth, client prompt-build time, and TTFT — not tokens. On subscription providers tokens are flat-rate anyway.

## Note for Micah (accepted trade-off)

`store: true` means xAI retains the conversation server-side for its retention window (undocumented length). Inputs already transit xAI regardless; this adds persistence. Called out so it's a decision, not a surprise.

## Scenarios (approved by Micah, 2026-07-08)

New file `features/llm/api/responses/stateful.feature` (wire-shape style of
`api/responses/api.feature`: grover:chatgpt transport through the real
responses adapter).

```gherkin
Scenario: cycle 2 chains from cycle 1's response id and sends only the new tool results
  Given the isaac EDN file "config/models/snuffy.edn" exists with:
    | path           | value          |
    | model          | snuffy-codex   |
    | provider       | grover:chatgpt |
    | context-window | 128000         |
    | stateful       | true           |
  And the isaac EDN file "config/crew/oscar.edn" exists with:
    | path  | value  |
    | model | snuffy |
  And the crew "oscar" allows tools: "exec"
  And the built-in tools are registered
  And the following sessions exist:
    | name      | crew  |
    | trash-can | oscar |
  And the following model responses are queued:
    | model        | type      | id     | tool_call | arguments           | content |
    | snuffy-codex | tool_call | resp-1 | exec      | {"command": "true"} |         |
    | snuffy-codex | text      | resp-2 |           |                     | done    |
  When the user sends "count the cans" on session "trash-can"
  Then outbound HTTP request 1 matches:
    | key        | value |
    | body.store | true  |
  And outbound HTTP request 1 has no body.previous_response_id
  And outbound HTTP request 2 matches:
    | key                       | value                |
    | body.previous_response_id | resp-1               |
    | body.store                | true                 |
    | body.input.#count         | 1                    |
    | body.input.0.type         | function_call_output |
```

```gherkin
Scenario: without stateful, every cycle resends the full context stateless
  (identical fixture minus the `stateful` row)
  ...
  Then outbound HTTP request 2 matches:
    | key                | value                |
    | body.store         | false                |
    | body.input.#count  | 3                    |
    | body.input.0.role  | user                 |
    | body.input.2.type  | function_call_output |
  And outbound HTTP request 2 has no body.previous_response_id
```

(The `#count 3` / `input.2` rows are the planner's read of the followup
format — adjust values to the real item count if it differs; the contract is
count + first-is-user + last-is-tool-output.)

```gherkin
Scenario: a previous-response-not-found reply resets state and retries with full context
  (fixture as scenario 1, with responses queued:)
    | model        | type       | id     | tool_call | arguments           | status | message                           | content |
    | snuffy-codex | tool_call  | resp-1 | exec      | {"command": "true"} |        |                                   |         |
    | snuffy-codex | http-error |        |           |                     | 404    | Response with id resp-1 not found |         |
    | snuffy-codex | text       | resp-3 |           |                     |        |                                   | done    |
  When the user sends "count the cans" on session "trash-can"
  Then outbound HTTP request 2 matches:
    | key                       | value  |
    | body.previous_response_id | resp-1 |
  And outbound HTTP request 3 matches:
    | key               | value |
    | body.store        | true  |
    | body.input.#count | 3     |
  And outbound HTTP request 3 has no body.previous_response_id
  And the log has entries matching:
    | level | event             | provider |
    | :info | :chat/state-reset | chatgpt  |
  And session "trash-can" has transcript matching:
    | type    | message.role | message.content |
    | message | user         | count the cans  |
    | #*      | #*           | #*              |
    | message | assistant    | done            |
```

Design point this scenario pins: the chain-miss 404 is retried locally with a
state reset — it must NOT classify as a provider wall (`:unavailable?`, 3tvq)
and must not defer the hail.

```gherkin
Scenario: a new turn starts a fresh chain — no previous_response_id carried across turns
  (fixture as scenario 1, plus a third queued text response "again" id resp-3)
  When the user sends "count the cans" on session "trash-can"
  And the user sends "count them again" on session "trash-can"
  Then outbound HTTP request 3 matches:
    | key               | value |
    | body.store        | true  |
    | body.input.0.role | user  |
  And outbound HTTP request 3 has no body.previous_response_id
```

State lives at most one turn: turn 2 opens with full rebuilt context and a
fresh chain, keeping compaction/suspend-resume/transcript-as-truth untouched.

Optional cheap variant (planner-suggested, not required): model
`stateful false` overriding a provider template's `true` — the override-off
direction of the layering.

### New step machinery (worker builds)

1. **`Then outbound HTTP request N matches:` / `has no <key>`** — indexed
   variants of the existing last-request steps.
2. **`#count` path segment** — array-length assertion in match tables.
3. **Queued model responses gain an `id` column** — grover's responses-wire
   replies carry scripted response ids.
4. **Mid-loop `http-error` rows** serve as that cycle's reply (scenario 3's
   404 must reach the adapter's chain-reset handling, not short-circuit).

## Acceptance

- [ ] Scenarios green in isaac-agent
- [ ] One-time on zanebot: a grok-composer work turn's cycle-2+ request bodies drop from ~1MB to KB-scale (verify via `:llm/http-request :body-chars` in server.log)
- [ ] chatgpt turns byte-identical in behavior (store:false, no chaining fields)
