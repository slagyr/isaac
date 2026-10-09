---
# isaac-r5j4
title: Strip caller_crew before MCP tool calls
status: completed
type: bug
priority: high
tags:
    - mcp
created_at: 2026-09-27T01:40:18Z
updated_at: 2026-09-27T01:49:35Z
---

Every Linear MCP tool whose input schema sets `additionalProperties: false` fails. `list_issues`, `list_teams`, and `list_projects` return `Unrecognized key: "caller_crew"`. `get_workspace` succeeds because its schema declares no parameters and does not forbid extra keys.

## What is happening

`isaac.drive.turn/announce-tool-call!` injects `caller_crew` into the arguments of every tool call, next to `session_key`. Built-in tools read that key (`fs_bounds` uses it for crew-scoped paths).

`isaac.mcp.runtime/mcp-arguments` is the gate in front of an MCP server. It drops `session_key`, `state_dir`, and any function (`progress!`). It forwards `caller_crew`. Linear validates the forwarded map against its schema and rejects the unknown key. The model is not sending `caller_crew`. A bare call fails the same way.

## Fix

Drop `caller_crew` in `mcp-arguments`, the same way `session_key` is dropped. Do not add the key to Linear's schemas. Those schemas are the server's. Isaac tools that need `caller_crew` still receive the full argument map, because `handler-arguments` only strips keys for non-builtin tools and `mcp-arguments` runs inside the MCP handler.

## Acceptance

Extend `isaac-mcp/spec/isaac/mcp/runtime_spec.clj`, the example "strips injected keys and callables before calling the server". The fake server records the argument map it was called with. Executing `lens__catalog` with `caller_crew` set, alongside `session_key`, `state_dir`, and `:progress!`, reaches the server without `caller_crew`, `session_key`, or `state_dir`. The call still succeeds.

`bb spec` in isaac-mcp passes.

## Outcome

Landed in isaac-mcp 9422702. `mcp-arguments` drops `caller_crew` with `session_key` and `state_dir`. The runtime spec records the map the fake server receives and expects only `query`. `bb spec` for that file: 29 examples, 0 failures. Skiff's pin is that sha and the server was restarted onto it.
