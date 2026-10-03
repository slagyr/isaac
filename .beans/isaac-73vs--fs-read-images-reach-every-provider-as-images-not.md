---
# isaac-73vs
title: fs__read images reach every provider as images, not just Claude Code over MCP
status: in-progress
type: feature
priority: normal
created_at: 2026-10-03T00:53:56Z
updated_at: 2026-10-03T00:54:52Z
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

## Implementation conflict (2026-10-03)

`bb jvm-features features/tool/image_results.feature` runs 5 examples, 5 failures. Anthropic and Responses images are absent, Chat Completions and `:vision false` report `Error: path outside allowed directories: /target/test-state/pixel.png`. Diagnostic from `ensure-path-allowed` showed `:cwd "/target/test-state", :global nil, :crew nil`: all five scenarios configure only `log.output`, never `defaults.tools.directories.allow` or crew directory permissions. `names/path-allowed?` intentionally denies when no grants exist. The scenarios' expected image is incompatible with the existing fail-closed filesystem policy. The planner must add a directory grant to the scenario config and re-baseline; worker may not edit baselined feature text. Ollama also shows a continuation prompt in request 2 instead of the expected tool reply; re-test once fs/read is permitted.

Checkpoint: branch `isaac-agent bean/isaac-73vs` at `17f9227` contains in-progress provider transports and binary-capable fixture. Unit smoke: `bb spec --focus spec/isaac/agent/llm/followup_spec.clj --focus spec/isaac/agent/llm/messages_spec.clj --focus spec/isaac/agent/llm/responses_spec.clj` 83 examples, 0 failures. Next: after planner re-baselines, fix native `bb features` reflection error (native gherclj `enrich-throwable`), verify Ollama and Responses request shapes, add complete specs, run `bb ci`, gate, land. Do not mark green until full acceptance passes.
