---
# isaac-ckt7
title: Anthropic messages API emits invalid empty text content blocks
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-25T22:31:13Z
updated_at: 2026-06-25T22:47:49Z
---

## Context
Crew "main" (claude via Anthropic "messages" API) started failing LLM calls with:
`invalid_request_error: messages: text content blocks must be non-empty`

This appeared after the messages API effort (llm-api berth, proper content blocks for tools/framing/caching in filter-messages-anthropic and followup).

Other crews/paths (chat-completions, responses) were more tolerant of empty content.

## Reproduction
- Session using crew main + claude (after tool-using turn or compaction).
- Transcript contains entry with effective content == "" (e.g. compaction summary="", toolResult with "", assistant response with no text, or sanitized user msg).
- Next turn: build-transcript-messages + filter-messages-anthropic + messages.clj build produces `{:type "text", :text ""}` (or array containing one).
- API rejects.

See "tell me about our session" flows + memory tools + compaction.

## Root cause
In `isaac-agent/src/isaac/llm/prompt/builder.clj`:
- `filter-messages-anthropic`:
  - toolResult: `(when text ... (text-block text))`  — `""` is truthy
  - user/assistant: `when-let [content (text-blocks ...)]` — `text-blocks("")` yields `[{:type "text" :text ""}]`
- `text-blocks` / `content->text` return "" or lists with empty blocks for blank input.
- Compaction stores `:summary` from `response-content` (which can be "") directly as `{:role "user" :content ""}`.
- No `not (str/blank? ...)` guards (unlike `build-system-text`).
- `followup-messages` (messages.clj) and framing can also feed blocks.
- The messages effort made us send *arrays of blocks* for Anthropic (required for the API), exposing the blanks that string content used to hide.

## Acceptance
- `filter-messages-anthropic` (and text-blocks/content->text callers for anthropic) never emit a text block with blank/empty `:text`.
- Compaction always produces a non-empty summary (fallback to minimal text if model returns none; or re-prompt requirement).
- Tool results and assistant messages with no text content are either dropped or normalized to non-blank before block creation.
- All existing `bb features` (anthropic paths) and specs in isaac-agent pass.
- Add coverage: transcript with blank entries → anthropic filter/build produces no empty blocks (unit or feature step).
- `isaac prompt --crew main` (claude) after tool compaction no longer produces the error.
- No change to non-anthropic filters or behavior when content is legitimately non-blank.

## Notes / open questions
- Should we also guard at storage time (never append blank assistant/toolResult)?
- Compaction prompt says "Output only the summary" — model sometimes emits only tool_use and no text.
- Related to prior messages effort (ho18 family) and compaction beans.

## Handoff
Harden the anthropic path in builder.clj:
- Change guards to `(when (and text (not (str/blank? text))) ...)`
- Update `text-blocks` to filter non-blank text parts.
- In compaction: if `summary` blank after response, fall back or ensure non-empty.
- Update followup if needed.
- Add test case exercising blank content for anthropic filter.
- Verify on zanebot main crew after fix.

Run `bb features` in isaac-agent and relevant specs.
