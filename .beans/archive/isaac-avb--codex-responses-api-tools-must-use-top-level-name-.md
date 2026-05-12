---
# isaac-avb
title: "Codex responses API: tools must use top-level name format, not nested function"
status: completed
type: bug
priority: high
created_at: 2026-04-15T19:16:49Z
updated_at: 2026-04-15T19:29:36Z
---

## Description

The chatgpt.com/backend-api/codex/responses endpoint requires tools formatted as:
{type: function, name: read, description: ..., parameters: {...}}

Isaac sends chat completions format:
{type: function, function: {name: read, description: ..., parameters: {...}}}

The endpoint silently ignores malformed tools, so GPT-5.4 never sees them and never makes tool calls. Marvin can't use tools.

build-tools-for-request in prompt/builder.clj needs a provider-specific formatter, same pattern as the message formatter.

features/providers/openai/dispatch.feature — 1 @wip scenario

## Acceptance Criteria

@wip scenario passes. Marvin uses tools through GPT-5.4 in IDEA.

