---
# isaac-s63
title: "Chat command options - agent, model, resume, session"
status: completed
type: task
priority: normal
created_at: 2026-04-01T14:57:40Z
updated_at: 2026-04-01T15:42:48Z
---

## Description

Implement chat command options per features/chat/options.feature.

## Options
--agent <name>     Agent to use (default: main)
--model <alias>    Override the agent's default model
--resume           Resume most recent CLI session for the agent
--session <key>    Resume specific session by key
--help             Show help for chat command

## Behavior Changes
- --model resolves alias to provider/model via config
- Context window resolved from model config (remove hardcoded 32768)
- --resume finds latest CLI session by updatedAt
- --session resumes specific key regardless of recency

## One Session Per Key
Creating a session for an existing key resumes it (no duplicate files). Fix current bug where create-session! always generates a new UUID.

## Feature Files
features/chat/options.feature
features/session/storage.feature (new scenario: creating for existing key resumes)

