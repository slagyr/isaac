---
# isaac-q9j6
title: Leg 3 — the MCP turn route moves to the claude-code module as /claude/turns/:id
status: draft
type: feature
priority: high
tags:
    - claude-code
    - server
    - mcp
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T05:26:16Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decision 5). Independent of leg 1.

Repo: **isaac-claude-code** (receives), **isaac-server** (sheds `server/mcp.clj` and its route).

The twenty-line JSON-RPC-over-HTTP shim moves into the claude-code module and is contributed via `:isaac.server/route` (later `:isaac.http/route`) at `/claude/turns/:id`; the mcp-bridge URL builder in `claude_cli.clj` follows. The per-turn registry `isaac.mcp.turns` stays in the agent. Existing mcp_turn_registry.feature scenarios move to the claude-code repo with the new path.

Scenarios: none new — `features/llm/mcp_turn_registry.feature` moves to isaac-claude-code with the new path; the old path answering 404 is a one-time acceptance check.
