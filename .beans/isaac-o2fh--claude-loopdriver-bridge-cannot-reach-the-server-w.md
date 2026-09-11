---
# isaac-o2fh
title: 'claude LoopDriver bridge cannot reach the server: wrong default port, no auth token, and ''pending'' treated as failed'
status: completed
type: feature
priority: high
created_at: 2026-09-08T18:55:31Z
updated_at: 2026-09-08T19:19:16Z
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

## Handoff

isaac-claude-code branch: bean/isaac-o2fh @ aecf628 (base origin/main@53faf58)
isaac-server branch: bean/isaac-o2fh @ 521c1b8 (base origin/main@fa39543)

Worker **scrapper**@isaac-work-2. Do not pin registry from this worker.

claude-code 0.1.6: `--server` from config `:server :port` (default 6674), `--token` from `:server :auth :token`; provider `:mcp-server-url`/`:mcp-token` still override. `mcp-failed?` only on status `failed` (or connected+zero tools); `pending` proceeds.

isaac-server 0.1.13: mcp-bridge 401 → JSON-RPC `-32001` `"unauthorized"` + `:mcp-bridge/unauthorized` log, never plain-text Unauthorized.

Acceptance: claude-code `bb features features/llm/api/claude_driver.feature` 15/15, `@wip` removed; `bb features` 29; `bb spec` 55/0 fail (3 pending @real). isaac-server `bb features features/server/mcp_bridge.feature` 5/5 including JSON-RPC auth error; `bb features` 72; `bb spec` 222. Field check + registry pin remain planner/verifier after pin. Negative log assertion reused existing `the log has no entries matching:`.



## Landed on main (2026-09-08)

main-sha: isaac-claude-code aecf628a7e03a5c1ddffefbd5347e5e70d164ebe
main-sha: isaac-server 521c1b88296773fe86a151e5af29040acdf5e786

## Verify note (perceptor@isaac-verify, hail 4300fdcf)

Hermetic gates:
isaac-claude-code @ aecf628:
- bb features features/llm/api/claude_driver.feature → 15 examples, 0 failures, 58 assertions
- bb features → 29 examples, 0 failures, 106 assertions
- bb spec → 55 examples, 0 failures, 178 assertions, 3 pending @real
- Feature: @wip removed on two planted o2fh scenarios; negative log step wording reused existing "the log has no entries matching:"; other 13 driver scenarios unchanged
- Module version 0.1.6

isaac-server @ 521c1b8:
- bb features features/server/mcp_bridge.feature → 5 examples, 0 failures, 12 assertions (JSON-RPC auth error covered)
- bb features → 72 examples, 0 failures, 196 assertions
- bb spec → 222 examples, 0 failures, 437 assertions
- Module version 0.1.13

Field check + registry pin are train/planner (bean: AFTER pin). Live smoke not run. No ~/.isaac mutation.



## Deployed (2026-09-08 ~19:3xZ) — module 0.1.6 (aecf628) + server 0.1.13 (521c1b8)
Smoke: reply `mcp-loop-ok` with a toolResult — still via FALLBACK. Progress: `:claude/mcp-status :status "connected" :tools 0` (URL + token now right; the bridge connects), then `:claude/driver-fallback :reason :mcp-failed` because Claude Code discovered zero tools. Next seam: the bridge's tools/list for the registered turn returns nothing — registry mismatch (driver registers into one registry, the server route reads another) or an empty tools payload. Being traced by the planner.
