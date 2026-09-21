---
# isaac-e2tb
title: Configurable temperature on the model entry
status: draft
type: feature
priority: normal
created_at: 2026-09-21T14:27:41Z
updated_at: 2026-09-21T14:27:41Z
---

## Today

Isaac never sends a temperature. `:temperature` is listed in chat-completions'
`wire-fields` (`isaac-agent/src/isaac/llm/api/chat_completions.clj`), but
nothing sets it. The model schema has no such field, and the messages,
responses and ollama adapters never mention it. Every model runs at its
server's default. GLM's live request body keys on zanebot were
`(:messages :model :parallel_tool_calls :reasoning_effort :stream
:stream_options :tools)`.

Other harnesses tune it per model. OpenCode's `provider/transform.ts` sets
1.0 for glm-4.6/4.7, minimax-m2 and kimi-k2 thinking variants, and leaves
Claude unset.

## Design

- **`:temperature` on the model entry** (`models/<id>.edn`): a number, validated
  to a sane range (0 to 2). It hot-reloads like the rest of model config.
- **Only sent when set.** With no `:temperature`, the request is
  byte-identical to today. Isaac doesn't pick a number for anyone.
- **Each adapter maps it to its own wire shape:**
  - chat-completions and responses: top-level `temperature`
  - messages (Anthropic): top-level `temperature`
  - ollama: `options.temperature`
  - claude-cli: not applicable. The CLI owns its own sampling, so drop the
    setting and log it once at debug. Don't fail the turn.
- **Some models reject it.** Some reasoning models accept only their default
  temperature and answer a 400 to anything else. Because it's only sent when
  the user configures it, that's the user's choice. The error should still
  make the cause obvious (name the field, name the model), not surface as a
  generic 400.

## Relation to other beans

- isaac-5n68 (model-family defaults) can supply a default temperature per
  family once this field exists. This bean is the field and the plumbing; 5n68
  is the defaults.
- `top_p` has the same shape. Leave it out until someone needs it.

## Done when

- `:temperature` is in the model schema; `isaac config validate` accepts a
  number in range and rejects anything else
- no `:temperature` means byte-identical requests (spec, per adapter)
- a configured value reaches the wire in each adapter's shape (spec, per
  adapter)
- claude-cli ignores it without failing
- editing it on a running server takes effect with no restart
- `bb verify` and `bb jvm-spec` are both green
