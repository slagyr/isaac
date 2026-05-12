---
# isaac-yqz
title: "Session keys - construction, parsing, and routing"
status: completed
type: task
priority: normal
created_at: 2026-03-31T19:48:54Z
updated_at: 2026-03-31T21:56:16Z
---

## Description

Implement session key handling per features/session/keys.feature. OpenClaw-convention keys: agent:<agentId>:<channel>:<chatType>:<conversationId>. Key construction from components, parsing back to components, thread key derivation, and delivery route tracking (lastChannel, lastTo) updated on message append.

