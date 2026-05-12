---
# isaac-z45
title: "Prompt builder - compose soul, history, and tools into Ollama request"
status: completed
type: task
priority: normal
created_at: 2026-03-31T19:49:00Z
updated_at: 2026-03-31T22:46:25Z
---

## Description

Implement prompt building per features/session/prompt_building.feature.

## Resolution Chain
agent config → model alias → provider/model string
agent config → workspace dir → SOUL.md (system prompt)

For now, agents and models are set up via Given tables in tests. Config resolution (isaac-4kc) handles real file-based resolution later.

## Ollama Request Shape
{
  "model": "qwen3-coder:30b",        // resolved from model alias
  "stream": true,
  "messages": [
    {"role": "system", "content": "..."},  // from SOUL.md
    {"role": "user", "content": "..."},    // from transcript
    {"role": "assistant", "content": "..."}, // from transcript
    ...
  ],
  "tools": [
    {"type": "function", "function": {"name": "...", "description": "...", "parameters": {...}}}
  ]
}

## Prompt Composition Rules
1. System message first (agent's soul from SOUL.md)
2. Conversation history from transcript (skip session header, use message entries)
3. After compaction: summary becomes first user message, only post-compaction messages follow
4. Tool definitions from agent's tool config

## Token Estimation
- Pre-send: chars/4 heuristic for estimating prompt size (good enough for compaction threshold)
- Post-send: use Ollama's prompt_eval_count and eval_count from response for accurate tracking

## Feature File
features/session/prompt_building.feature

