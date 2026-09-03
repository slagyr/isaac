---
# isaac-zocg
title: Per-turn tool registry + local MCP endpoint + `isaac mcp-bridge` stdio command
status: draft
type: feature
tags:
    - claude-cli
    - mcp
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:07:34Z
parent: isaac-tuk1
blocked_by:
    - isaac-jllj
---

The CLI can only receive tools as MCP servers (spike isaac-khgy). Isaac needs: (1) a per-turn registry in the running server: turn-id → {tool-fn, allowed tools with schemas (crew ACL + session directories), session-key}; registered by the loop driver for the turn's lifetime, cleared at turn end; (2) a local MCP-over-HTTP (or WS) endpoint on the server (authenticated like /cli) that serves initialize / tools/list / tools/call for a turn-id — tools/list = the turn's allowed tools rendered as MCP input schemas (names `<ns>__<name>` as today; the CLI prefixes mcp__<server>__); tools/call → the registry's tool-fn (so record-tool-call! persists + comms fire); results capped by isaac's existing output caps (the CLI rejects >~25k-token results, khgy L3); (3) `isaac mcp-bridge --turn <id>` — a stdio MCP server (the shape the CLI spawns via --mcp-config) that proxies JSON-RPC to that endpoint; pattern: isaac-cli-proxy ↔ isaac-cli-server. Where it lives: registry + endpoint in isaac-agent/isaac-server (server surface); the bridge command can live in the isaac-claude-code module or core — decide in review (core keeps the module thin; module keeps core free of CLI concerns). Scenarios (@wip): (1) tools/list for a registered turn returns exactly the crew's allowed tools with schemas; (2) tools/call executes through the turn's tool-fn and the transcript shows the toolCall/toolResult pair; (3) a call for an unregistered/ended turn is refused; (4) an oversized result is capped before it returns; (5) the bridge command round-trips initialize/tools/list/tools/call over stdio against the endpoint. Draft until reviewed.
