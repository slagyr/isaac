---
# isaac-3cdi
title: "Validator: built-in providers should not require entity file"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T01:11:41Z
updated_at: 2026-04-28T02:50:04Z
---

## Description

The config validator (src/isaac/config/loader.clj:475-479) rejects any
model whose :provider doesn't appear in providers/*.edn entity files.
But several providers are built into Isaac's runtime — drive/dispatch.clj
already knows how to talk to them without any user config:

  anthropic, claude-sdk, grover, ollama,
  openai-codex, openai-chatgpt, grok

Symptom from zanebot:

  :event :config/reload-failed
  :error "models.claude.provider references undefined provider \"anthropic\""

models/claude.edn says {:provider :anthropic} and there is no
providers/anthropic.edn — the runtime would handle this fine, only
the validator complains.

## Fix shape

Seed the validator's known-provider set with the built-in list
(probably exported as a public var from drive/dispatch.clj or a new
isaac.llm.registry ns). User-supplied providers/<name>.edn entries
are then **overrides** for built-ins (api-key, base-url, etc.), not
the only way to declare a provider.

## Definition of done

- A model file referencing :anthropic loads cleanly with no
  providers/anthropic.edn on disk
- A providers/anthropic.edn override still applies on top of built-in
  defaults
- Unknown providers (e.g. :foo) still fail validation with a clear
  message listing what's known
- Unit spec covers all three cases
- bb features still green

## Related

- isaac-vb8m (hooks) and isaac-x5vo (session_state) both touch provider
  resolution — make sure the built-in list lives somewhere they can
  also consume

## Notes

Built-in provider ids are now accepted by config validation, provider entity files remain optional overrides, and unknown providers list the known built-ins. bb spec and targeted composition feature pass. Full bb spec && bb features now fail only on unrelated bead isaac-i5i3 (ACP reconnect status chunks include newlines).

