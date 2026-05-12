---
# isaac-4kc
title: "Configuration resolution - OpenClaw-compatible config and workspace loading"
status: completed
type: task
priority: normal
created_at: 2026-03-31T22:01:23Z
updated_at: 2026-03-31T22:48:30Z
---

## Description

Implement config resolution with OpenClaw drop-in compatibility.

## Config File Resolution (fallback chain)
1. ~/.openclaw/openclaw.json — if found, use it
2. ~/.isaac/isaac.json — if found, use it
3. Neither — use defaults

## Workspace File Resolution
Same fallback: ~/.openclaw/workspace-<agentId>/ then ~/.isaac/workspace-<agentId>/

Workspace files read:
- SOUL.md — persona and tone (system prompt)
- AGENTS.md — behavior rules
- TOOLS.md — tool usage guidance
- USER.md — info about the user
- MEMORY.md — persistent memory

## Config Schema (match OpenClaw)
- agents.defaults — shared defaults (model, tool policy)
- agents.list[] — agent definitions (id, workspace, model override)
- models.providers — custom provider definitions (baseUrl, apiKey, api, models[])
- Model refs use provider/model format (e.g., ollama/qwen3-coder:30b)
- tools — built-in catalog with allow/deny policies

## Test Convention
Tests use target/test-state/ as the state directory. The Given step for agents writes SOUL.md from the table's soul column.

## Note
Early beads may hardcode paths. This bead replaces hardcoded paths with proper resolution.

