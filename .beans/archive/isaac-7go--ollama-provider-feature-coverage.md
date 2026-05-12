---
# isaac-7go
title: "Ollama provider feature coverage"
status: completed
type: feature
priority: high
created_at: 2026-04-08T17:52:25Z
updated_at: 2026-04-08T18:52:40Z
---

## Description

Add provider-specific feature coverage for Ollama under features/providers/ollama.

## Scope
- Add fast messaging feature coverage for Ollama request format, response parsing, streaming, tool calling, and unreachable-server errors
- Add slow/live integration feature coverage for a real local Ollama server
- Keep Ollama provider behavior organized consistently with the other providers
- Cover clear error behavior when the Ollama server or requested model is unavailable

## Proposed feature files
- features/providers/ollama/messaging.feature
- features/providers/ollama/integration.feature

## Notes
- Ollama does not need a separate auth feature today
- Fast features specify provider contract behavior
- Slow features verify real local integration

