---
# isaac-70ud
title: "LLM reorg C: providers catalog with defaults"
status: completed
type: task
priority: normal
created_at: 2026-05-07T05:28:42Z
updated_at: 2026-05-07T16:15:26Z
---

## Description

Under umbrella isaac-uwso. Depends on A (isaac-4ca2).

Add isaac.llm.providers — a catalog of known LLM providers (anthropic, openai, openai-codex, openai-chatgpt, xai/grok, ollama) with their default config. Each entry carries:
- :api  — wire-spec id (e.g. :anthropic-messages, :openai-completions, :openai-responses)
- :base-url
- :auth (mode: :api-key, :oauth-device, :none)
- :models (default model ids the provider serves)

Today this defaulting is scattered: isaac.provider/simulated-provider-config, isaac.provider/real-provider-defaults, normalize, etc. Pull it into a single declarative catalog so config resolution becomes 'merge user config over catalog default for that provider'.

Designed to be promotable: when a provider grows substantial state (provider-specific model registries, OAuth flow logic), its entry can split into isaac.llm.providers.<name> without changing call sites.

## Acceptance Criteria

isaac.llm.providers exists with declarative entries for all built-in providers; isaac.provider/simulated-provider-config and real-provider-defaults retire (or become thin readers over the catalog); config resolution paths use the catalog; bb spec and bb features green.

## Notes

Verification failed: isaac.llm.providers exists and config resolution uses it, but the catalog entries do not satisfy the bead description. In src/isaac/llm/providers.clj:15-23 the entries do not all carry :base-url, :auth, and :models; all entries omit :models entirely, and several entries omit :base-url and :auth. bb spec and bb features are green.

