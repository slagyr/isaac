---
# isaac-05c
title: "prompt --crew doesn't resolve crew member's model or soul"
status: completed
type: bug
priority: high
created_at: 2026-04-15T15:06:13Z
updated_at: 2026-04-15T15:15:27Z
---

## Description

The prompt command's resolve-run-opts builds the agents map with a hardcoded 'main' key regardless of which crew member is requested. (get agents "ketch") returns nil, falling back to the default ollama model. The crew member's soul and model are never used.

Also missing: workspace SOUL.md fallback.

features/cli/prompt.feature — 2 @wip scenarios

## Acceptance Criteria

Both @wip scenarios pass with @wip removed. prompt --crew ketch uses ketch's model and soul.

