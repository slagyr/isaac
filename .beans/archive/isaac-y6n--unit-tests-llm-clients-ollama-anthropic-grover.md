---
# isaac-y6n
title: "Unit tests: LLM clients (Ollama, Anthropic, Grover)"
status: completed
type: task
priority: normal
created_at: 2026-04-02T00:16:12Z
updated_at: 2026-04-02T00:46:31Z
---

## Description

spec/isaac/llm/ollama_spec.clj, anthropic_spec.clj, grover_spec.clj are missing.

Cover:
- grover: echo mode, scripted mode, queue behavior, tool calls, streaming
- ollama: response parsing, error handling (don't need @slow for unit tests — mock HTTP)
- anthropic: response parsing, SSE parsing, auth header construction, error handling

Use TDD skill. Follow speclj conventions.

