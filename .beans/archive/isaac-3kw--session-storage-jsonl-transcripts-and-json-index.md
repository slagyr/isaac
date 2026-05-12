---
# isaac-3kw
title: "Session storage - JSONL transcripts and JSON index"
status: completed
type: task
priority: normal
created_at: 2026-03-31T19:48:48Z
updated_at: 2026-03-31T22:43:07Z
---

## Description

Implement session storage per features/session/storage.feature.

## Storage Layout
<stateDir>/agents/<agentId>/sessions/sessions.json — index
<stateDir>/agents/<agentId>/sessions/<sessionId>.jsonl — transcripts

For now, hardcode stateDir (tests use target/test-state/). Config resolution (isaac-4kc) will make it dynamic later.

## Transcript Format (JSONL, one entry per line)
- Line 1: session header {type: "session", id: <uuid>, timestamp: <epoch-ms>}
- Subsequent: message entries {type: "message", id: <uuid>, parentId: <prev-id>, timestamp: <epoch-ms>, message: {...}}
- Entries form a linked chain via parentId (null for header)

## Message Roles
- user: {role: "user", content: "..."}
- assistant: {role: "assistant", content: "..." or [...blocks], model: "...", provider: "..."}
- toolResult: {role: "toolResult", toolCallId: "...", content: "...", isError: false}
- Tool calls are content blocks in assistant messages: {type: "toolCall", id: "...", name: "...", arguments: {...}}

## Session Index (sessions.json)
Minimal entry fields at creation: sessionId, sessionFile, updatedAt, channel, chatType, compactionCount (0), inputTokens (0), outputTokens (0), totalTokens (0). Add OpenClaw fields as needed, matching their key names.

## Step Conventions
- Given the following sessions exist: — injects state directly (no creation logic)
- When the following sessions are created: — exercises actual creation code path
- Both use the same table shape

## Feature File
features/session/storage.feature

