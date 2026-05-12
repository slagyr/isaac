---
# isaac-19i
title: "Rename isaac agent command to isaac prompt"
status: completed
type: task
priority: normal
created_at: 2026-04-14T04:42:24Z
updated_at: 2026-04-14T04:48:12Z
---

## Description

isaac agent is a single-turn command that sends one prompt and exits. The name agent is confusing now that personalities are called crew. Rename to isaac prompt. src/isaac/cli/agent.clj becomes src/isaac/cli/prompt.clj. features/cli/agent.feature becomes features/cli/prompt.feature. isaac agent must return unknown command after rename.

## Acceptance Criteria

isaac prompt works. isaac agent returns unknown command. cli/agent.clj deleted. features/cli/prompt.feature passes.

