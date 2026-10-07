---
# isaac-qqtc
title: Remote MCP tools get Isaac's internal arguments
status: draft
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

Draft until scenarios are written. Likely: a remote tool whose schema does not declare `request_id` is called without that key, and a built-in tool still receives `session_key` and `request_id`.
