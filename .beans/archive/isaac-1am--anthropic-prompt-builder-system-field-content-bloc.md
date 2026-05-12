---
# isaac-1am
title: "Anthropic prompt builder - system field, content blocks, cache breakpoints"
status: completed
type: task
priority: normal
created_at: 2026-04-01T16:05:09Z
updated_at: 2026-04-01T16:20:47Z
---

## Description

Build Anthropic-specific prompt format per features/providers/anthropic/messaging.feature.

## Differences from Ollama
- system is a separate top-level field (array of content blocks), not a message
- system blocks use {type: "text", text: "..."} format
- cache_control: {type: "ephemeral"} on system block and penultimate user message
- messages use content block arrays for tool calls (type: "tool_use" not "toolCall")
- max_tokens is required

## Request Shape
{
  "model": "claude-sonnet-4-6",
  "max_tokens": 4096,
  "system": [{"type": "text", "text": "soul...", "cache_control": {"type": "ephemeral"}}],
  "messages": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi"},
    {"role": "user", "content": [{"type": "text", "text": "...", "cache_control": {"type": "ephemeral"}}]}
  ],
  "tools": [{"name": "...", "description": "...", "input_schema": {...}}]
}

## Testing
Use Grover for fast tests. Prompt format scenarios don't hit any API.

## Feature File
features/providers/anthropic/messaging.feature (request format and caching scenarios)

