---
# isaac-9lv
title: "Chat provider dispatch - route to correct LLM client"
status: completed
type: task
priority: high
created_at: 2026-04-01T18:04:53Z
updated_at: 2026-04-01T20:41:46Z
---

## Description

The chat loop is hardcoded to Ollama. It needs to dispatch to the correct provider based on model config.

## Feature File
features/chat/providers.feature

## Current State
- chat.clj calls ollama/chat-stream directly
- hardcodes provider as "ollama" in transcript entries

## Change
Resolve the provider from config and dispatch to the right client:
- ollama → isaac.llm.ollama
- anthropic → isaac.llm.anthropic
- openai-compatible (grok, openai, etc.) → isaac.llm.openai-compat

This is the natural point to extract a provider dispatch function or protocol.
The chat loop should not need to know which provider it's talking to.

## Scope
- Provider dispatch in chat loop (streaming + non-streaming)
- Correct provider name in transcript entries
- --model flag switches provider
- Compaction chat-fn should also dispatch correctly

