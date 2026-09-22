---
# isaac-2sxf
title: 'claude-code driven turn: a session-limit error mid-turn takes the fence fallback and ends :reply with the CLI init event as the answer — it is weather'
status: todo
type: bug
priority: high
tags:
    - claude-code
    - provider
    - hail
created_at: 2026-09-22T22:51:31Z
updated_at: 2026-09-22T22:51:31Z
---

## Observed (zanebot, 2026-09-22 22:05Z, agent 0.1.81 hotfix, isaac-claude-code 34dbfa7)

Hail afbfec8f → isaac-work-2, bean isaac-ruom, provider `:claude`, model
claude-opus-5, driven mode (CLI runs the tool loop over MCP). 79 tool cycles
ran 21:40:36Z–22:05:45Z. Then:

- 22:05:57 `:claude/title-side-call`, 22:05:58 `:mcp/tools-listed`, 22:05:59
  `:claude/mcp-status`, `:claude/driver-exit`, then
  `:claude/driver-fallback :stderr "You've hit your session limit · resets
  4:40pm (America/Phoenix)"` — the seat's 5-hour window closed mid-turn.
- The fallback re-ran the CLI without MCP; the turn ended 22:06:10 with
  `:ended-by :reply`, `hail/turn-ended :outcome :delivered`.
- The "reply" stored as the last assistant entry is the CLI's init event:
  `{"type":"system","subtype":"init","cwd":"/","session_id":…,"tools":[…]}`.
  That text was delivered as the turn's answer.

## Expected

A usage-limit / rate-limit / auth error from the CLI, at any point in a driven
turn, is provider weather (isaac-f3hq: the drive parks and resumes; isaac-v64q
for the Responses path is the same rule). The driver returns an error response
classified `:rate-limited` (or `:unavailable?` true) with the CLI's message,
never a text reply; a `system`/`init` stream event is never assistant content.
The fence fallback (isaac-zz6d) is for MCP-init failure only, not for a limit
hit after cycles ran.

## Scenarios (isaac-claude-code features, fake CLI script)

- driven turn, N cycles, then the CLI emits the session-limit result → the
  response is an error with `:error :rate-limited`, the message carries the
  reset text, no assistant text entry is written, no fallback invocation.
- the same on the first invocation (0 cycles).
- MCP-init failure still takes the fence fallback (existing scenario stays).

## Related

isaac-benp (auth sniffing over the whole stream), isaac-9gcs (dropped stream
burns an attempt), isaac-f3hq (weather), isaac-9azm, isaac-ruom (the turn).
