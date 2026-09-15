---
# isaac-lddb
title: Anthropic transcript replay drops tool calls and turns tool results into user text
status: completed
type: bug
priority: normal
tags:
    - agent
    - tools
    - anthropic
created_at: 2026-09-15T17:24:58Z
updated_at: 2026-09-15T18:22:05Z
---

## Problem

When an Anthropic-shaped provider's prompt is rebuilt from the transcript (next turn, after mid-turn compaction, overflow retry via `rebuild-chat-request`), every tool call in the history is dropped and every tool result is replayed as plain user text.

- `src/isaac/llm/api/messages.clj:37–38` `extract-messages` builds history with `builder/build-transcript-messages … builder/filter-messages-anthropic`.
- `src/isaac/llm/prompt/builder.clj` `filter-messages-anthropic`: `(tool-call? msg) → nil` (the assistant tool call is removed), a user message immediately before a tool call is also removed, and `toolResult` becomes `{:role "user" :content [text-block]}`.
- Mid-turn the shape is correct: `messages.clj` `followup-messages` (~211) pairs `tool_use` blocks in the assistant message with `tool_result` blocks keyed by `tool_use_id`. Only the replay from disk loses it.

Effects:
- The model's own history shows results with no call that produced them, and adjacent user-role messages where the API expects assistant `tool_use` / user `tool_result` pairs.
- Prompt caching and the model's sense of what it already did both degrade on long tool-heavy sessions after any rebuild.
- Affects every Anthropic-shaped provider (`:anthropic` models, e.g. zanebot `models/claude.edn`, `haiku.edn`, `opus.edn`, `sonnet.edn`).

## Proposal

- Replay transcript tool calls as assistant `tool_use` blocks (id, name, input) and tool results as user `tool_result` blocks with the matching `tool_use_id`, preserving `is_error`.
- Stop dropping the user message that precedes a tool call.
- Build on isaac-gihe: once a batch is one assistant entry with results in call order, one assistant message carries every `tool_use` in the batch and the next user message carries every `tool_result` in call order.

## Depends on

- isaac-gihe (batch-shaped transcript).

## Decisions

- Decision (2026-09-15, Micah approved planning): consecutive tool-call entries group into one assistant message of `tool_use` blocks and consecutive `toolResult` entries into one user message of `tool_result` blocks. isaac-gihe (landed `6fb50a3`) writes a batch as one assistant entry; older transcripts have one call per entry (no migration, per isaac-gihe) — grouping replays both alike and avoids back-to-back assistant messages the Anthropic API rejects.
- Decision (2026-09-15): `tool_result` content keeps the existing `truncate-tool-result` cap; `is_error` is set from the entry's `:isError`.
- Decision (2026-09-15): the user message preceding a tool call is kept.
- Cache breakpoints: `apply-cache-breakpoints` marks the last block of the penultimate user message; with `tool_result` user messages that can be a `tool_result` block, which the API accepts. Cover placement with a spec, not a scenario.

## Scenarios

Approved 2026-09-15 (Micah: "print scenarios, pause only for new steps" — none invented). File `features/llm/api/anthropic_replay.feature` (new), committed on the bean branch.
1. a tool batch replays as one assistant message of tool_use blocks, then one user message of tool_result blocks in call order
2. older one-call entries and the user message before them replay with ids paired
3. a failed tool result replays with is_error

Steps reused: `default Grover setup`, `config:`, `the built-in tools are registered`, `the isaac EDN file … exists with:`, gated/streaming test tools, `the following model responses are queued:`, `the user sends … via memory comm`, `the following sessions exist:`, `session … has transcript:` (`toolCall` / `toolResult` rows), `the prompt … on session … matches:` (builds with `messages-api/build` when the crew model's provider is anthropic). Message indices may need retuning to the builder's framing blocks; keep the assertions.

## Acceptance

```
ISAAC_GIT=1 bb features features/llm/api/anthropic_replay.feature
bb ci
```

Specs: `filter-messages-anthropic` grouping (batch entry, consecutive one-call entries, mixed text), `is_error` passthrough, truncation cap inside `tool_result`, cache breakpoint placement on a `tool_result` user message.

## Verification (2026-09-15, plan session)

Verify pass.
- `filter-messages-anthropic` (`src/isaac/llm/prompt/builder.clj`) rewritten: tool calls → assistant `tool_use` blocks; results → user `tool_result` blocks keyed by `:toolCallId`/`:id`, with `is_error`, the `truncate-tool-result` cap, and `(empty)` for blank results; adjacent calls/results group; the preceding user message is kept; a result with no id falls back to the old user-text shape.
- Specs: 6 new/rewritten in `builder_spec.clj` (the old "drops blank tool results" became "(empty) keeps the call paired"), 1 new cache-breakpoint spec in `anthropic_spec.clj`. Red before the change, green after (87 examples across builder/anthropic/messages specs).
- Feature `features/llm/api/anthropic_replay.feature`: 3 scenarios green; all 3 confirmed red against unfixed `6fb50a3` (tool_use missing, preceding user message dropped, no is_error).
- Gates: branch `bb ci` 1616 specs / 761 features; merged with isaac-4erp (`22bba5c`) 1625 specs / 762 features; 0 failures, exit 0.
- Squash-merged as isaac-agent `565d994`; branch `bean/isaac-lddb` deleted. Not released or deployed.

## Deployed (2026-09-15)

Shipped to zanebot in isaac-agent 0.1.69 (`3e3ef7e`, registry `ea4cff3d`), restarted 18:34:01Z. Boot: http 401, `resume/scan-complete` (0 requeued), `runner/started :components 8`, Discord ready, no validation errors or stale-delivery removals. Smoke: `isaac prompt -M gpt` → pong; `session/compaction-check` logs the tally gauge (no drift ratio). The `modules upgrade` step needed the fresh-root workaround (isaac-784x).
