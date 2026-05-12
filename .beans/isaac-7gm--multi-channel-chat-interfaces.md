---
# isaac-7gm
title: "Multi-channel chat interfaces"
status: draft
type: feature
priority: normal
tags:
    - "deferred"
created_at: 2026-04-08T15:07:10Z
updated_at: 2026-04-08T15:37:19Z
---

## Description

Epic: Isaac should support chatting through multiple channels and frontends beyond the current CLI path.

## Channels to track
- TUI
- Web UI
- Discord
- iMessage
- Google Chat
- Remote TUI
- Other future channels

## Goals
- Define a channel abstraction around session routing, message delivery, and user identity
- Reuse the same core chat/session/tooling behavior across channels
- Keep channel-specific transport, formatting, and auth concerns isolated from core chat logic
- Support both local interactive channels and remote/networked channels

## Likely sub-work
- Channel/session model and routing rules
- TUI implementation
- Web UI implementation
- Channel adapters for messaging platforms
- Remote terminal/TUI transport
- Channel-specific auth and delivery concerns
- Testing strategy per channel

## Notes
- This is an epic-level planning item, not a single implementation bead
- Tools, memory, webhooks, and observability may all intersect with this work

