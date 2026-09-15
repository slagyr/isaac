---
# isaac-lddb
title: Anthropic transcript replay drops tool calls and turns tool results into user text
status: draft
type: bug
priority: normal
tags:
    - agent
    - tools
    - anthropic
created_at: 2026-09-15T17:24:58Z
updated_at: 2026-09-15T17:24:58Z
blocked_by:
    - isaac-gihe
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

## Open questions (not decided)

- Truncation: `tool_result` content today goes through `truncate-tool-result` as text; keep the same cap inside the block?
- Cache breakpoints (`apply-cache-breakpoints`, `penultimate-user-index`) assume the current message shape — verify placement once `tool_result` user messages appear.

## Acceptance (draft — scenarios TBD)

- A transcript containing a tool batch, rebuilt for an Anthropic provider, yields an assistant message whose content holds `tool_use` blocks for every call, followed by a user message holding `tool_result` blocks in call order with matching ids.
- The user message before the tool call is present in the rebuilt prompt.
