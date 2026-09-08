---
# isaac-o2fh
title: 'claude LoopDriver bridge cannot reach the server: wrong default port, no auth token, and ''pending'' treated as failed'
status: todo
type: bug
priority: high
created_at: 2026-09-08T18:55:31Z
updated_at: 2026-09-08T18:55:31Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (`claude_cli.clj`: `mcp-server-url` defaults to http://127.0.0.1:7733, `mcp-server-token` reads :mcp-token / ISAAC_SERVER_TOKEN, `mcp-failed?` treats a non-connected init status as failure) and isaac-server (`mcp-bridge`: plain-text `Unauthorized` on tools/list). Child of isaac-tuk1; follow-up to isaac-lrvb (0.1.5, deployed 18:33Z — fence gone, status logged, fallback safe).

## Evidence (zanebot, 2026-09-08 18:51Z, module 0.1.5)
- Smoke: `:claude/mcp-status :servers [{:name "isaac" :status "pending"}] :tools 0` → `:claude/driver-fallback :reason :mcp-failed`; the fence path then answered `mcp-loop-ok` correctly.
- zanebot server listens on **6674** (`server.port` unset → default); auth token = `server.auth.token ${SERVER_AUTH_TOKEN}` from ~/.isaac/.env; `providers.claude` = `{:type "claude"}` (no :mcp-server-url / :mcp-token); ISAAC_SERVER_URL / ISAAC_SERVER_TOKEN are not set anywhere. So the driver wrote `--server http://127.0.0.1:7733` and no `--token`.
- Bridge probe from inside the server (`isaac mcp-bridge --turn probe --server http://127.0.0.1:6674`, no token): `initialize` answers locally (serverInfo isaac 0.1.0), `tools/list` returns the plain text `Unauthorized` (not a JSON-RPC error) — so even with the right port the bridge needs the token, and its error is not something Claude Code can parse.
- Claude Code's init event reports stdio servers as "pending" for roughly the first second; the driver's `mcp-failed?` treats anything not connected as failure and falls back immediately.

## Required
1. Driver resolves the bridge's `--server` from the server the turn runs inside of (config `:server :port`, default 6674, host 127.0.0.1) and `--token` from the server's configured auth token (config `:server :auth :token`, env-resolved) — provider-level `:mcp-server-url` / `:mcp-token` stay as explicit overrides only.
2. `mcp-failed?` fires only on status `failed` (or on a turn that ends with the model unable to see tools after connect); `pending` proceeds.
3. isaac-server `mcp-bridge`: auth failure on tools/list or tools/call is a JSON-RPC error (code -32001, message "unauthorized"), never plain text; the bridge logs `:mcp-bridge/unauthorized` to stderr so Claude Code's server log shows it.
4. Fake Claude Code: `mcp_status` pending then tool_use must run natively (scenario 2).

## Scenarios (@wip, planted isaac-claude-code 27a697a)
- the MCP config carries the running server's own URL and auth token
- a pending MCP server at init is not a failure — the turn proceeds and the tools arrive
Steps: existing (isaac EDN file exists with, fake scripted with, response is, transcript matching, log has entries matching, the MCP config handed to the fake names server running) + **NEW: Then the log does not have entries matching:** (negative log assertion; if the agent library already has one under another wording, use it).

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 15 scenarios green with @wip removed; `bb features && bb spec` green in isaac-claude-code; isaac-server `bb features features/server/mcp_bridge.feature` green with the JSON-RPC auth error covered
- Field check by the verifier via exec on zanebot AFTER the planner pins the released versions (module 0.1.6 + server if changed): `isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command'\''s output.'` → `mcp-loop-ok`; cli.log shows `:claude/mcp-status` with isaac connected (or pending followed by tools > 0), `:claude/driver-exit :result-event true`, NO `:claude/driver-fallback`; transcript has the exec toolCall/toolResult pair. The planner records the deploy and re-hails verify for it.
