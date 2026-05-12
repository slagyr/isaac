---
# isaac-new
title: "ACP agent mode (epic)"
status: completed
type: feature
priority: normal
created_at: 2026-04-10T21:08:39Z
updated_at: 2026-04-13T21:14:55Z
---

## Description

Epic: Implement the Agent Client Protocol so Isaac can act as an ACP agent and be used by front-ends like Toad, Zed, IntelliJ 2026.1, Neovim, Emacs, Marimo, and anything built with the Vercel AI SDK ACP provider.

## Architecture
- New command: `isaac acp` — JSON-RPC 2.0 over stdin/stdout (NDJSON)
- ACP sessions are Isaac-native: keys use the `acp` channel, e.g. `agent:main:acp:direct:<uuid>`
- Working directory inherits from the process cwd at startup
- Existing chat flow (process-user-input!) handles prompts; ACP layer is a thin adapter

## Reachable front-ends (with one implementation)
- Toad (standalone terminal UI by Will McGugan)
- Zed editor
- IntelliJ IDEA 2026.1+
- Neovim, Emacs, Eclipse (via community plugins)
- Marimo notebooks
- Any Node.js/web app using Vercel AI SDK ACP provider

## Protocol surface
### Required (client → agent)
- initialize
- session/new
- session/prompt
- session/cancel (notification)

### Required (agent → client)
- session/update (notification) — text chunks, tool states

### Optional for later
- authenticate
- session/load
- session/set_mode
- session/request_permission
- fs/read_text_file, fs/write_text_file
- terminal/* methods

## Feature files
- features/acp/initialization.feature
- features/acp/session.feature
- features/acp/prompt.feature
- features/acp/streaming.feature
- features/acp/cancellation.feature
- features/acp/tools.feature

## Child beads (ordered)
1. JSON-RPC stdio infrastructure
2. initialize + session/new
3. session/prompt (non-streaming)
4. session/update streaming
5. session/cancel
6. Tool call state updates
7. End-to-end with Toad

This is an epic/umbrella bead. Child beads should reference it.

