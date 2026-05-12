---
# isaac-134
title: "Model alias resolution - resolve aliases without provider/model format"
status: completed
type: task
priority: high
created_at: 2026-04-01T20:46:05Z
updated_at: 2026-04-01T20:47:25Z
---

## Description

resolve-model-info falls through to Ollama when the model ref has no "/" separator. Model aliases like "claude" need to be looked up in the agents.models config map first.

## Bug
--model claude → parse-model-ref returns nil (no "/") → defaults to provider "ollama", model "claude" literally → 404

## Fix
In resolve-model-info, before calling parse-model-ref:
1. Check if model-ref is a key in agents.models
2. If found, use the alias entry's model and provider
3. Then resolve the provider config as normal

## Resolution Chain
--model claude → agents.models.claude → {model: "claude-sonnet-4-6", provider: "anthropic"} → resolve-provider "anthropic" → {baseUrl: "https://api.anthropic.com", ...}

## Feature File
features/chat/options.feature — "Override model resolves alias to different provider"

