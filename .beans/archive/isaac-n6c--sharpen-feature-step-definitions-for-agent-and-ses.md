---
# isaac-n6c
title: "Sharpen feature step definitions for agent and session precision"
status: completed
type: task
priority: low
created_at: 2026-04-09T14:34:37Z
updated_at: 2026-04-09T16:24:54Z
---

## Description

Existing Gherkin steps use implicit state (current-key, first agent from key) to resolve which agent/session is being operated on. This works for single-agent single-session scenarios but is imprecise and breaks down with multi-agent or ambiguous state.

## New/Revised Steps

**Given:**
- `agent {string} has sessions:` — replaces `the following sessions exist:`. Table supports `#comment` column.
- `session {string} has transcript:` — replaces `the following messages are appended:` (Given). Handles all entry types (message, compaction, toolCall, toolResult) via `type` column, mirroring the Then assertion table shape.

**When:**
- `sessions are created for agent {string}:` — replaces `the following sessions are created:` and `the following thread sessions are created:`. Thread sessions use `parentKey`/`thread` columns in the same table.
- `entries are appended to session {string}:` — replaces `the following messages are appended:` (When). All entry types.
- `the user sends "{string}" on session {string}` — replaces `the user sends "{content:string}"`. Full process-user-input! flow.

**Then:**
- `agent {string} has {int} session(s)` — replaces `the session listing has {int} entries`
- `agent {string} has sessions matching:` — replaces `the session listing has entries matching:`
- `session {string} has {int} transcript entries` — replaces `the transcript has {int} entries`
- `session {string} has transcript matching:` — replaces `the transcript has entries matching:`
- `the prompt "{string}" on session {string} matches:` — replaces `a prompt is built for the session` + `the prompt matches:`. Builds and inspects in one step.

## Steps to Drop (18 total, no replacement)
- `the session has been compacted with summary {string}` — absorbed by `session {string} has transcript:`
- `the session totalTokens exceeds 90% of the context window` — use `agent {string} has sessions:` with calculated values and `#comment`
- `the session contains a tool result of {int} characters` — use realistic content in transcript table with small context window
- `{int} exchanges have been completed` — rewrite scenario with explicit setup
- `the next user message is sent` — use `the user sends`
- `compaction is triggered` — set up threshold in session, use `the user sends`
- `the prompt is sent to the LLM` — use `the user sends`
- `the prompt is streamed to the LLM` — use `the user sends`
- `the model responds with a tool call` — no-op narrative step
- `the session is loaded for key {string}` — no implicit state to set
- `the key {string} is parsed` / `the parsed key matches:` — delete scenario, unit tests cover this
- `compaction is triggered before sending the prompt` — use transcript matching
- `the tool result in the prompt is less than {int} characters` — rework truncation scenario
- `the tool result preserves content at the start and end` — rework truncation scenario
- `an error is reported indicating the server is unreachable` — use log + transcript assertions
- `the transcript has no new entries after the user message` — use transcript matching
- `the prompt has a token estimate greater than 0` — becomes a row in prompt matches table
- `response chunks arrive incrementally` — tentatively dropped; revisit if needed

## Infrastructure
- Add `#comment` column support to table infrastructure (ignored by matchers)

## Definition of Done
- All feature files updated to use new steps
- Old step definitions deleted
- No implicit current-key or agent resolution in session steps
- bb features and bb spec pass

