---
# isaac-dah
title: "prompt command must set cwd on session"
status: completed
type: bug
priority: normal
created_at: 2026-04-15T19:00:12Z
updated_at: 2026-04-16T16:25:36Z
---

## Description

The prompt command creates sessions without cwd. The ACP command sets cwd from the startup directory but prompt doesn't. This means: no AGENTS.md loading, no project context, the model doesn't know where it is.

features/cli/prompt.feature needs a scenario asserting cwd is set.

## Acceptance Criteria

prompt command sets cwd on created sessions. AGENTS.md loads correctly for prompt-created sessions.

