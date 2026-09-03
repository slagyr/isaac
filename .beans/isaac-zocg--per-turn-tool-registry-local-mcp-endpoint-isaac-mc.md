---
# isaac-zocg
title: Per-turn tool registry + local MCP endpoint + `isaac mcp-bridge` stdio command
status: todo
type: feature
priority: normal
tags:
    - claude-cli
    - mcp
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:36:15Z
parent: isaac-tuk1
blocked_by:
    - isaac-jllj
---

The CLI can only receive tools as MCP servers (spike isaac-khgy). Isaac needs: (1) a per-turn registry in the running server: turn-id → {tool-fn, allowed tools with schemas (crew ACL + session directories), session-key}; registered by the loop driver for the turn's lifetime, cleared at turn end; (2) a local MCP-over-HTTP (or WS) endpoint on the server (authenticated like /cli) that serves initialize / tools/list / tools/call for a turn-id — tools/list = the turn's allowed tools rendered as MCP input schemas (names `<ns>__<name>` as today; the CLI prefixes mcp__<server>__); tools/call → the registry's tool-fn (so record-tool-call! persists + comms fire); results capped by isaac's existing output caps (the CLI rejects >~25k-token results, khgy L3); (3) `isaac mcp-bridge --turn <id>` — a stdio MCP server (the shape the CLI spawns via --mcp-config) that proxies JSON-RPC to that endpoint; pattern: isaac-cli-proxy ↔ isaac-cli-server. Where it lives: registry + endpoint in isaac-agent/isaac-server (server surface); the bridge command can live in the isaac-claude-code module or core — decide in review (core keeps the module thin; module keeps core free of CLI concerns). Scenarios (@wip): (1) tools/list for a registered turn returns exactly the crew's allowed tools with schemas; (2) tools/call executes through the turn's tool-fn and the transcript shows the toolCall/toolResult pair; (3) a call for an unregistered/ended turn is refused; (4) an oversized result is capped before it returns; (5) the bridge command round-trips initialize/tools/list/tools/call over stdio against the endpoint. Draft until reviewed.



## Decisions (2026-09-03, Micah: "I like it")

1. The bridge command lives in **core** (isaac-server's cli table; it is the client of a server route on the same host). isaac-claude-code contains only the driver.
2. Transport: **plain HTTP JSON-RPC** on the existing server — `POST /mcp/turns/{turn-id}`, one MCP message per request, behind the server-wide bearer middleware (`:server :auth :token`, handed to the bridge via `ISAAC_SERVER_TOKEN` in the CLI's mcp-config env).
3. Scoping: the driver registers a random single-use turn id at turn start with {session-key, tool-fn (the drive's record-tool-call! partial), allowed tools + schemas}; cleared at turn end on every outcome; unknown/ended turn → JSON-RPC error -32001 "turn not active", nothing executes.
4. Names/schemas: isaac's wire names as-is (`ns__name`); the CLI's `mcp__isaac__` prefix is stripped by the driver when correlating events.
5. Results: isaac's per-tool output caps apply before return (keeps every result under the CLI's ~25k-token limit); tool failures return `isError true`, never a JSON-RPC error.

Split of production: registry + JSON-RPC handler fn in **isaac-agent** (`isaac.mcp.turns` — pure functions over the registry, no HTTP); route + bridge command in **isaac-server** (route via the `:isaac.server/route` berth like /cli; `mcp-bridge` in the server's :isaac/cli table). Tests follow production.

## Features (`@wip`)
- isaac-agent `features/llm/mcp_turn_registry.feature` @ 932682f — 5 scenarios: tools/list exact; tools/call persists the pair through the drive's tool-fn; failing tool → isError; oversized result capped; cleared turn refused, nothing executes.
- isaac-server `features/server/mcp_bridge.feature` @ f25d5e4 — 4 scenarios: route behind the token (401); tools/list over the route; unknown turn → JSON-RPC -32001 with HTTP 200; `isaac mcp-bridge` stdio round trip (initialize answered locally, notification dropped, list + call forwarded).

## Step ledger

| step | status |
|------|--------|
| default Grover setup / built-in tools registered / crew allows tools / sessions exist / config: / transcript matching / transcript not matching / an Isaac root at / the Isaac server is started / the response status is / the response body has … equal to / the exit code is 0 | reuse |
| **Given a turn "…" is registered for session "…"** (server variant adds `with tools "…"`) | **NEW — fixture: registers a turn with the session's crew tools and a real record-tool-call! tool-fn** |
| **And the turn "…" is cleared** | **NEW** |
| **When an MCP request is handled for turn "…":** (docstring JSON) | **NEW — agent-side: calls the handler fn directly, no HTTP** |
| **Then the MCP response matches:** / **has N tools** / **has no "…" key** / **text is at most N characters** | **NEW — assertions over the last MCP response (dot-path table like the server's response-body step)** |
| **When the client sends POST "…" with header "…" and body:** / **with body:** (docstring) | **NEW — server-side; POST variants of the existing GET step** |
| **When the mcp-bridge command is run for turn "…" with stdin:** / **the mcp-bridge stdout has N JSON lines** / **line N has … equal to / matching** | **NEW — runs the contributed command as a subprocess against the started test server** |

## Acceptance

    cd isaac-agent && bb features features/llm/mcp_turn_registry.feature && bb spec spec/isaac/mcp
    cd isaac-server && bb features features/server/mcp_bridge.feature features/server/auth.feature && bb spec
    both: full bb features && bb spec (exit codes)
The server pins the agent SHA that ships the registry. Remove @wip when green.
