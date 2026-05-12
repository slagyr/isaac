---
# isaac-802
title: "OpenAI Codex OAuth - device code flow for ChatGPT Plus/Pro"
status: completed
type: task
priority: normal
created_at: 2026-04-04T03:49:49Z
updated_at: 2026-04-07T23:43:14Z
---

## Description

Connect to OpenAI's Codex models using ChatGPT Plus/Pro subscription via OAuth device code flow.

## Flow
1. Isaac initiates device code request to OpenAI
2. User gets a code + URL printed to terminal
3. User opens URL on any device, logs in with ChatGPT account, enters code
4. Isaac polls for completion, receives subscription tokens
5. Tokens stored locally in <stateDir>/auth.json or similar
6. Use tokens with OpenAI-compatible endpoint for Codex models (GPT-5.x variants, GPT-5.2, etc.)

## Reference
- OpenCode implements this in v1.1.11+ via /connect command
- Community plugin for headless: https://github.com/tumf/opencode-openai-device-auth
- Uses OpenAI's official Device Code flow (same as Codex CLI)
- This is first-party OAuth, NOT a reverse-engineered API

## Research Needed
- OpenAI's device code endpoint URL
- Token format and refresh mechanism  
- Which models are available via Codex subscription tokens
- The exact API endpoint (may differ from api.openai.com)

## Config
Provider entry with auth: "oauth-device" or similar:
  {"name": "openai-codex", "auth": "oauth-device", "api": "openai-compatible"}

## CLI
bb isaac auth login --provider openai-codex
  → prints code + URL
  → polls for completion
  → stores tokens

## Skip features first
Implement and test manually. Feature it after confirming it works.

