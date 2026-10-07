---
# isaac-qqtc
title: Remote MCP tools get Isaac's internal arguments
status: todo
type: bug
priority: high
created_at: 2026-10-07T15:25:46Z
updated_at: 2026-10-07T15:25:46Z
---

Likely repo: **isaac-agent**. Reported by Micah, 2026-10-06. Re-checked against installed library `3031edd` (installed Oct 6, 23:28).

## Symptom

Since `3031edd`, every call to the Linear MCP server that takes arguments fails with `Unrecognized key: request_id`. Calls with no arguments, like `get_workspace`, still work. Linear rejects any argument key it does not recognise.

## Where the extra keys are added

`src/isaac/agent/drive/turn.clj`, lines 1577–1581. Before every tool call, the arguments get:

- `"session_key"`
- `"caller_crew"`
- `"request_id"` (new in the Oct 6 builds `f05a1fe`, `e05614a`, `dc42dfe`, and `3031edd`)
- `:progress!` (a function)

Separately, `src/isaac/agent/session/compaction.clj:460` adds `"session_key"`, `"state_dir"`, and `"session_store"`.

## Where they should be removed

`src/isaac/agent/tool/registry.clj`, lines 327–330, `handler-arguments`. For a tool that is not built in, it `dissoc`s `"crew"`, `"session_key"`, `"state_dir"`, `:crew`, `:session_key`, and `:state_dir`.

That denylist is out of date. It does not remove `request_id`, `caller_crew`, `session_store`, or `:progress!`. Each new internal key leaks to remote servers until someone remembers to add it here.

## Fix (agreed)

For tools that are not built in, switch to an allowlist: keep only the argument keys the tool's own input schema declares, and drop everything else. Tool definitions carry `:parameters` with `:properties` (see `tool/builtin.clj`, and `mcp/turns.clj:47`, which maps `:parameters` to `inputSchema`).

Micah's sketch was truncated at `get-in tool [:parameters :`. The intended shape is: the allowed names are the keys of `:parameters :properties`; anything else is dropped before the remote call. Built-in tools keep the injected keys.

## Acceptance

`bb features features/turn.feature:80` in isaac-mcp.

The scenario is on isaac-mcp main `51beffd`, `features/turn.feature` line 80. Lens catalog declares only `query`. A turn calls `lens__catalog` with `{"query":"keys"}`. The drive injects `session_key`, `caller_crew`, `request_id`, and `:progress!` on the way in. The tool result text must be exactly `query`.

## Fix (2026-10-07, Micah, corrected)

The leak is in **isaac-mcp**, not isaac-agent. `isaac.tool.mcp.runtime/mcp-arguments` is the gate in front of a remote server. It drops `session_key`, `state_dir`, `caller_crew`, and any function. It forwards `request_id` and `session_store`. Linear's schema sets `additionalProperties: false` and rejects `request_id`.

Keep the allowlist Micah asked for, but put it in `mcp-arguments`: keep only the keys of the tool's own `:parameters` `:properties` (`inputSchema` as registered). Drop everything else, including a key the model invented. A tool whose schema declares no properties is called with no arguments.

Do not change `handler-arguments` in isaac-agent. Built-in tools, and isaac-mcp itself, read the injected keys before that gate. `fs_bounds` reads `caller_crew` and `session_store`. A built-in still receives `session_key` and `request_id`.

## Planner note (2026-10-07)

Repo corrected from isaac-agent to isaac-mcp after reading `mcp-arguments` on main `a7c301e`. Same class of bug as isaac-r5j4, which added `caller_crew` to that denylist. The denylist is the bug. The scenario reuses the lens fixture: `query` of `keys` makes `lens_mcp.bb` reply with the sorted argument keys it received. No new step.

feature-baseline: isaac-mcp 51beffd932807239d088e0e59423325272201f3b
feature-blob: isaac-mcp features/turn.feature df5844240af0cee31db926671fe80e6ee5e3eb93 80
