---
# isaac-y3wm
title: 'claude-code: spawn failures are reported as mcp-unavailable'
status: draft
type: bug
priority: high
created_at: 2026-11-11T15:08:00Z
updated_at: 2026-11-11T15:08:00Z
---

## Problem

The claude-code provider labels every exception in a driven turn as
`:mcp-unavailable`. In `claude_cli.clj` `invoke!`, the outer `catch` maps any
`Exception` to `mcp-init-weather`, and so does the `register-mcp-turn!` guard.
A failed launch of the `claude` program therefore looks like an MCP outage.

Seen live on 2026-11-11: a JDK package upgrade under a running server broke
process spawning (`Cannot run program "claude": Failed to exec spawn helper …
JDK version mismatch`). For 8.5 hours every turn parked as
`suspended (mcp-unavailable)`, and the attention channel repeated that label
every half hour. The real error appeared only in unrelated
`episodes/seal-failed` warnings. Nothing on the turn path logged it.

## Goal

A turn's weather names what actually failed, and the log says why.

- If the CLI process cannot be started (an `IOException` from
  `process/process`, e.g. "Cannot run program"), report weather with its own
  reason, e.g. `:reason :cli-unavailable`, and put the exception message in
  `:message`. Keep it weather, so the turn still parks and resumes; just stop
  calling it MCP.
- `:mcp-unavailable` is used only when the MCP setup itself fails
  (`register-mcp-turn!` / listener start) or the CLI's init event shows the
  `isaac` server failed (`mcp-failed?`).
- Every weather result logs one line with the reason and message (e.g.
  `:claude/turn-weather`), so a parked turn always leaves its cause in the
  server log.
- The attention text for a parked turn includes that message, not just the
  reason keyword.

## Acceptance (to be written as @wip scenarios before baseline)

- A driven turn whose CLI launch throws "Cannot run program" parks with reason
  `:cli-unavailable` (not `:mcp-unavailable`), and the log carries the
  exception message.
- A driven turn whose MCP listener fails to start still parks as
  `:mcp-unavailable`.
- The parked-turn attention message includes the failure message.

## Likely repo scope

isaac-claude-code (`api/claude_cli.clj`). The attention wording may live in
isaac-agent (`drive/weather.clj`).

## Related

isaac-u1oe — the server survives a JDK upgrade on disk (prevents the underlying spawn failure).
