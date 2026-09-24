---
# isaac-mbnb
title: 'isaac-claude-code: Claude CLI talks MCP over HTTP straight to the per-turn listener — no stdio bridge process'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-24T17:58:50Z
updated_at: 2026-09-24T18:26:23Z
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

## Handoff

- Branch: `bean/isaac-mbnb` (on top of the isaac-1q9m commit).
- isaac-claude-code sha: `591b5a0` — isaac-mbnb: Claude CLI speaks MCP over HTTP straight to the per-turn listener.
- CLI version consulted for the config shape: **claude-code 2.1.281** on this machine (`which claude` → `/Users/micahmartin/.local/bin/claude`). Confirmed empirically, not just from `--help`: ran `claude mcp add --transport http testsrv http://127.0.0.1:9999 --header "Authorization: Bearer abc123" -s local` in a scratch dir and read back the project entry it wrote to `~/.claude.json` — it produced exactly `{"type":"http","url":"http://127.0.0.1:9999","headers":{"Authorization":"Bearer abc123"}}`, matching the bean's documented shape. Removed the test entry afterward (`claude mcp remove testsrv -s local`); did not touch any other MCP entries in `~/.claude.json`.
- Files changed: `src/isaac/llm/api/claude_cli.clj` (`write-mcp-config!` now takes `[url nonce]` and writes the HTTP shape; `process-classpath` deleted; `subprocess-env` back to single-arity — `ISAAC_MCP_NONCE` env-var plumbing removed since nothing reads it anymore, the nonce now lives only in the config's Authorization header), `src/isaac/llm/mcp_listener.clj` (delegates JSON-RPC classification to a new `isaac.llm.mcp-route/dispatch`; added `:request-method` check → 405 on non-POST), `src/isaac/llm/mcp_route.clj` (new `dispatch` fn: answers `initialize` itself with the request's `protocolVersion` echoed + `serverInfo` + `capabilities.tools`, returns `{:status 202 :body nil}` for any notification, otherwise still delegates to the unchanged `handle-turn`/`isaac.mcp.turns/handle` path), `src/isaac/mcp_bridge/main.clj` + `spec/isaac/mcp_bridge/main_spec.clj` deleted, `deps.edn` (dropped the deleted spec file from the `:spec` alias's explicit JVM compile list), `src/isaac-manifest.edn` (version 0.1.19 → 0.2.0, minor bump).
- Features: renamed `features/llm/mcp_bridge.feature` → `features/llm/mcp_transport.feature`; rewrote its scenarios for direct-to-listener HTTP (initialize echo + notification 202/empty + tools/list + tools/call in one scenario, wrong-bearer → 401, GET → 405, listener stopped at turn end → connection refused) using new gherclj steps in `spec/isaac/llm/mcp_route_steps.clj` (`the listener for turn "X" receives an MCP request:`, `... with bearer "Y":`, `a GET request is made to the listener for turn "X"`, `the listener for turn "X" is stopped`, plus `the MCP HTTP status is N` / `the MCP response body is empty` / `the MCP request fails to connect`). Also updated `features/llm/api/claude_driver.feature`'s two isaac-ejj3 scenarios in place (MCP config assertion now checks `type=http`/`url=`/`authorization=Bearer …` via a new `mcp-config-names-http-server` step registered as `the MCP config handed to the fake Claude Code names server "isaac" as HTTP:`, replacing the old bb-bridge-argv assertion; dropped the now-dead `(ISAAC_MCP_NONCE in env)` table-token check).
- Specs: rewrote the two `claude_driver_spec.clj` tests that asserted the old `:command "bb"`/`:args […]` shape and the `ISAAC_MCP_NONCE` env passthrough; added new `it`s to `spec/isaac/llm/mcp_listener_spec.clj` (initialize echo, notification 202/empty, GET 405) and `spec/isaac/llm/mcp_route_spec.clj` (`dispatch` initialize echo, `dispatch` notification never reaching the registry).
- Counts at this commit: `bb spec` 90 examples / 0 failures / 3 pending (real-CLI smokes, expected, unrelated); `bb features` 60 examples / 0 failures; `bb ci`'s `config-bypass-lint` and `lint-cli-host` both ok.
- Design note for review: `/claude/turns/:id` (the `isaac.http/route`, separate from the per-turn listener that `write-mcp-config!` actually points at) now goes through the same new `mcp-route/dispatch` — so it also answers `initialize` with an echoed `protocolVersion` and 202/empty-bodies notifications, where before it just forwarded everything to `isaac.mcp.turns/handle` (which has its own generic, non-echoing `initialize` handler). This seemed like the natural place to share the classification logic per the task's "implement in mcp_listener.clj/mcp_route.clj" phrasing, but it's a behavior change to a route I didn't otherwise touch — worth confirming nothing else depends on that route's old non-echoing `initialize` reply.
- **Real-CLI smoke still needed before the registry pin moves** (bean's own acceptance item, explicitly planner-owned): on a host with `claude` logged in, run the module's smoke / `isaac prompt --model claude-cli` with a prompt needing the exec tool, confirm `tools/call` reaches the listener, the reply carries the output, and the CLI's `mcp_status` event shows the `isaac` server connected. I did not run this — no logged-in `claude` session was available/appropriate to use from this worker context, and the bean reserves it for the planner.
