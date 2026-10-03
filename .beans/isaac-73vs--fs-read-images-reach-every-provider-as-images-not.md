---
# isaac-73vs
title: fs__read images reach every provider as images, not just Claude Code over MCP
status: todo
type: feature
created_at: 2026-10-03T00:53:56Z
updated_at: 2026-10-03T00:53:56Z
---

Likely repo: **isaac-agent**. Follow-up to isaac-m4o2 (Micah, 2026-10-02: images from fs__read must reach every model, not just Claude Code over MCP).

## Why

isaac-m4o2 changed fs__read at the root: it returns
`{:type "image" :media-type … :bytes … :path … :data <base64>}` for PNG,
JPEG, GIF and WebP. Only the MCP bridge knows that shape. Every other
provider's `followup-messages` puts the raw tool result into the next
request. Anthropic Messages passes the map as `tool_result.content` (an
invalid block), and the OpenAI paths stringify it, base64 and all. So since
m4o2, a non-MCP crew reading an image gets a broken or bloated request.

## Design

- Within the turn that read it, each provider carries the image in its own
  wire shape (the tool loop's in-memory request). The transcript keeps
  m4o2's `[image: <name>, <type>, <N> bytes]` note, so later turns and
  replays see the note only — the same lifetime Claude Code gives it.
- Anthropic Messages: `tool_result.content` = `[{type image, source {type base64, media_type, data}}]`.
- OpenAI Responses: `function_call_output.output` = `[{type input_image, image_url "data:<type>;base64,…"}]`.
  Verify the current Responses API accepts image items in function output
  before building; if it doesn't, use the Chat Completions split instead and
  re-cut that scenario with the planner.
- Chat Completions: the tool message carries the note (tool messages are
  text-only); a user message right after it carries `image_url` with a data URL.
- Ollama: same split, image in the follow-up message's `images`.
- grover:<provider> simulators render the same shapes so features can check them.
- Model config gains `:vision` (boolean, default true). `:vision false` gets
  only the note. Add it to the models schema.
- Replace m4o2's test-only `with-redefs` image fixture (MCP step) with a
  binary-safe step: `an image file "<name>" exists in the session working
  directory`. Keep m4o2's scenarios green.

## Acceptance

- isaac-agent `features/tool/image_results.feature` — "Anthropic Messages carries the image inside the tool_result"
- isaac-agent `features/tool/image_results.feature` — "OpenAI Responses carries the image as an input_image in the function_call_output"
- isaac-agent `features/tool/image_results.feature` — "Chat Completions follows the text-only tool message with a user message carrying the image"
- isaac-agent `features/tool/image_results.feature` — "Ollama follows the tool message with a message carrying the image"
- isaac-agent `features/tool/image_results.feature` — "a model configured without vision gets only the note"
- isaac-m4o2's scenarios (built_in.feature, mcp_turn_registry.feature) stay green.

feature-baseline: isaac-agent 30351533d7591740e43c36f04851debc40c26eae
feature-blob: isaac-agent features/tool/image_results.feature be95ed38f8af8f41a2047b087b5bc0851fef7f66 26
feature-blob: isaac-agent features/tool/image_results.feature be95ed38f8af8f41a2047b087b5bc0851fef7f66 58
feature-blob: isaac-agent features/tool/image_results.feature be95ed38f8af8f41a2047b087b5bc0851fef7f66 87
feature-blob: isaac-agent features/tool/image_results.feature be95ed38f8af8f41a2047b087b5bc0851fef7f66 118
feature-blob: isaac-agent features/tool/image_results.feature be95ed38f8af8f41a2047b087b5bc0851fef7f66 148
