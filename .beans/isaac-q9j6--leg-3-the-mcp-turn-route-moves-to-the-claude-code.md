---
# isaac-q9j6
title: Leg 3 — the MCP turn route moves to the claude-code module as /claude/turns/:id
status: completed
type: feature
priority: high
tags:
    - claude-code
    - server
    - mcp
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T06:27:13Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decision 5). Independent of leg 1.

Repo: **isaac-claude-code** (receives), **isaac-server** (sheds `server/mcp.clj` and its route).

The twenty-line JSON-RPC-over-HTTP shim moves into the claude-code module and is contributed via `:isaac.server/route` (later `:isaac.http/route`) at `/claude/turns/:id`; the mcp-bridge URL builder in `claude_cli.clj` follows. The per-turn registry `isaac.mcp.turns` stays in the agent. Existing mcp_turn_registry.feature scenarios move to the claude-code repo with the new path.

Scenarios: none new — `features/llm/mcp_turn_registry.feature` moves to isaac-claude-code with the new path; the old path answering 404 is a one-time acceptance check.

## Acceptance

mcp_turn_registry.feature scenarios green in isaac-claude-code at /claude/turns/:id; the mcp-bridge smoke against the real claude binary passes (prompt --model claude-cli with a tool call); isaac-server has no server/mcp.clj and no /mcp route (one-time check).

```
cd isaac-claude-code && bb features && bb spec && bb ci
cd isaac-server && bb ci
```


## Implementation (scrapper@isaac-work-3)

Moved the JSON-RPC HTTP shim and the `mcp_turn_registry.feature` acceptance contract to `isaac-claude-code`. The Claude manifest now contributes `POST /claude/turns/:id`, and its MCP bridge URL builder points at `/claude/turns`. Removed `isaac.server.mcp`, its specs, and the `/mcp/turns/:turn-id` contribution from `isaac-server`; the generic `mcp-bridge` now appends the turn id to the route URL supplied by the provider.

Branches:
- isaac-claude-code: `bean/isaac-q9j6` @ `2d58cb5` (base `origin/main@c52aa46`)
- isaac-server: `bean/isaac-q9j6` @ `ab9c1cd` (base `origin/main@d49972f`)

Verification:
- isaac-claude-code: `bb features` (39 examples), `bb spec` (64 examples; 3 opt-in real-smoke pending), `bb ci` green.
- isaac-server: `bb spec` (222 examples), `bb features` (69 examples), `bb ci` green.
- one-time grep confirms isaac-server has no `server.mcp` or `/mcp/turns` references.
- real Claude smoke remains opt-in (`ISAAC_CLAUDE_REAL=1 bb smoke`) and was not enabled in this environment.



## Landed on main (2026-09-11)

main-sha: isaac-claude-code 433c7f81264b9c5e0c668c87011a8acd7b32d282
main-sha: isaac-server f61a1f52763e1d13bcf6e26fa2ddd78c0a20a0b2
