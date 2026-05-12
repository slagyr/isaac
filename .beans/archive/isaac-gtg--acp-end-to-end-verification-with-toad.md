---
# isaac-gtg
title: "ACP: end-to-end verification with Toad"
status: completed
type: task
priority: low
created_at: 2026-04-10T21:11:07Z
updated_at: 2026-04-13T21:14:55Z
---

## Description

Manual verification that Isaac's ACP implementation works with Toad as the front-end.

## Scope
- Install Toad: curl -fsSL batrachian.ai/install | sh
- Register Isaac as an ACP agent in Toad's config
- Launch Toad, select Isaac, start a conversation
- Verify: chat works, responses stream, tool calls execute, sessions persist across restarts

## Notes
Isaac ACP must be on PATH or registered with an absolute path (IDEs/Toad often don't inherit shell PATH).

Also verify the same setup with:
- Zed (~/.config/zed/settings.json agent_servers)
- IntelliJ IDEA 2026.1 (~/.jetbrains/acp.json)

Parent epic: isaac-new

## Definition of Done
- Toad + Isaac: send/receive chat, streaming, tools, session resume
- Zed + Isaac: basic chat works
- IntelliJ + Isaac: basic chat works
- Document setup in README or docs/

