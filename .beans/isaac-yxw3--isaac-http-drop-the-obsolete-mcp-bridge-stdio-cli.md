---
# isaac-yxw3
title: 'isaac-http: drop the obsolete `mcp-bridge` stdio CLI (proxied a remote Claude CLI to /claude/turns with the server token)'
status: in-progress
type: task
priority: normal
created_at: 2026-09-24T18:37:44Z
updated_at: 2026-09-24T18:37:44Z
---

Micah, 2026-09-24: "Why does Zane still have an MCP bridge command?" — after isaac-1q9m removed the claude-code module's copy, `isaac help` on zanebot still listed `mcp-bridge  stdio MCP server that proxies a turn to POST /mcp/turns/{id}`. That one is isaac-http's `isaac.mcp-bridge.cli` (isaac-zocg, 2026-09-04): a stdio MCP server for a Claude CLI running elsewhere, proxying JSON-RPC to the server's turn route with `--token`/`ISAAC_SERVER_TOKEN` — the server-wide bearer we are retiring. The route moved to the claude-code module (isaac-q9j6) and the driver has used a per-turn loopback listener since; as of isaac-mbnb the CLI speaks HTTP MCP straight to that listener. No caller remains (grep across agent/claude-code/http/acp).

## Acceptance
- [ ] `:isaac/cli {:mcp-bridge …}` removed from `resources/isaac-manifest.edn`; `src/isaac/mcp_bridge/cli.clj` and its spec deleted; `isaac help` no longer lists it (scenario or spec asserting the manifest's CLI map has only `:http`).
- [ ] `/claude/turns/:id` (claude-code) untouched.
- [ ] `bb ci` green; version 0.1.24 → 0.1.25; registry repin; zanebot upgrade + restart.

Repo scope: isaac-http.
