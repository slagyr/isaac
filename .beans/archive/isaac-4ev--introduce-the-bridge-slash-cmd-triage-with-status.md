---
# isaac-4ev
title: "Introduce the bridge: slash-cmd triage with /status"
status: completed
type: feature
priority: normal
created_at: 2026-04-12T18:52:08Z
updated_at: 2026-04-12T19:25:20Z
---

## Description

## Problem

User input currently goes straight to `process-user-input!` (the LLM turn). There's no layer to intercept meta-commands like `/status`. The user has no way to inspect session state without breaking character or reading logs.

## Design

Introduce `isaac.session.bridge` — a triage layer between the channel and the turn executor. The bridge:
1. Checks if input starts with `/`
2. If yes: dispatches to a command handler, returns structured data
3. If no: forwards to the turn executor (existing `process-user-input!` flow)

The bridge returns a data map for commands. The channel decides how to render it (CLI prints formatted text, ACP emits a structured notification).

### /status returns:
```clj
{:agent "main"
 :model "echo"
 :provider "grover"
 :session-key "agent:main:cli:direct:user1"
 :session-file "8ee09048.jsonl"
 :turns 4
 :compactions 2
 :tokens 5000
 :context-window 32768
 :context-pct 15
 :soul-source "~/.isaac/workspace-main/SOUL.md"
 :tool-count 3
 :cwd "/Users/micahmartin/Projects/isaac"}
```

### Integration points:
- `chat/loop.clj`: call bridge instead of process-user-input! directly
- `acp/server.clj`: call bridge in the prompt handler
- Bridge delegates to turn executor for non-command input

## New step definitions needed
- `Then the output matches:` (table) — each row is a regex pattern that must match somewhere in stdout

## Acceptance

- `bb features features/chat/commands.feature` passes with @wip removed
- @wip removed from all four scenarios
- /status works in CLI chat
- /status works through ACP (Toad shows structured response)

## Acceptance Criteria

All four @wip scenarios in features/chat/commands.feature pass with @wip removed. /status works in both CLI and ACP.

