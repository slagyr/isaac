---
# isaac-mbnb
title: 'isaac-claude-code: Claude CLI talks MCP over HTTP straight to the per-turn listener — no stdio bridge process'
status: in-progress
type: feature
priority: high
created_at: 2026-09-24T17:58:50Z
updated_at: 2026-09-24T17:58:50Z
---

Micah, 2026-09-24: "if we can drop the [bridge] and go directly to HTTP, that sounds much more efficient. Why wouldn't we do that?"

## Today
Per driven turn the driver starts an httpkit listener on 127.0.0.1 (random port, random bearer nonce; `isaac.llm.mcp-listener`), writes a temp `--mcp-config` naming a **stdio** server `bb -m isaac.mcp-bridge.main --turn ID --url URL`, and the Claude CLI spawns that bb process, which relays each JSON-RPC line to the listener (`isaac.mcp-bridge.main`: answers `initialize` itself, drops notifications, POSTs everything else with the nonce). One babashka start per turn (~1–2 s) and an extra namespace.

## Change
- The MCP config names an **HTTP** server: `{"mcpServers":{"isaac":{"type":"http","url":"http://127.0.0.1:<port>","headers":{"Authorization":"Bearer <nonce>"}}}}` — Claude Code's Streamable-HTTP MCP client. Verify the exact keys against the installed CLI (`claude mcp add --transport http --help` and the docs); `"type":"http"` + `"headers"` is the documented shape.
- The listener absorbs the two things the bridge did: answer `initialize` (protocolVersion echo, serverInfo, `tools` capability) and accept notifications (`notifications/initialized`, cancelled) with 202/empty body. Honour `Accept: application/json, text/event-stream` by answering plain JSON (Streamable HTTP allows a JSON response to a POST); GET on the endpoint → 405 unless the real CLI needs an SSE stream. Add `Mcp-Session-Id` only if the CLI demands it.
- Delete `isaac.mcp-bridge.main` + its spec once the direct path is proven; `process-classpath` goes with it.
- Cancellation/termination semantics unchanged.

## Verify against the REAL CLI (planner, not the worker)
The driver's history (isaac-5xn7) says fake-CLI-green can fail on the real binary. Before the registry pin moves: on a host with `claude` (yopp has 2.1.274), run the module's smoke / `isaac prompt --model claude-cli` with a prompt that needs the exec tool, confirm `tools/call` reaches the listener and the reply carries the output, and check the CLI's `mcp_status` event shows the server connected. Record the CLI version in the handoff.

## Acceptance
- [ ] Feature (rename mcp_bridge.feature → mcp_transport.feature): the config the driver writes is the HTTP shape with no `command`; a fake CLI POSTing `initialize`, `notifications/initialized`, `tools/list`, `tools/call` with the bearer gets correct MCP responses; wrong nonce → 401; listener stops at turn end.
- [ ] `isaac.mcp-bridge.main` deleted; `bb ci` green; manifest minor bump.
- [ ] Real-CLI smoke recorded (planner) before the registry pin moves.

Repo scope: isaac-claude-code (`claude_cli.clj` write-mcp-config!, `mcp_listener.clj`, `mcp_route.clj`, features). Land after isaac-1q9m.
