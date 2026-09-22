---
# isaac-2sxf
title: 'claude-code driven turn: a session-limit error mid-turn takes the fence fallback and ends :reply with the CLI init event as the answer — it is weather'
status: completed
type: bug
priority: high
tags:
    - claude-code
    - provider
    - hail
created_at: 2026-09-22T22:51:31Z
updated_at: 2026-09-22T23:45:28Z
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

## Handoff (worker, 2026-09-22)

**Root cause.** `invoke!` (isaac-claude-code `src/isaac/llm/api/claude_cli.clj`)
sent *every* is_error result event to `fence-fallback!`. A session-limit result
is an is_error result, so the driver re-ran the CLI without MCP, the second run
hit the same wall, and `parse-json-output` handed back the only thing on stdout
— the `system`/`init` event JSON — as the turn's reply. Nothing in the driver
ever looked at *what* the CLI said failed.

**Fix.** The CLI's own error signal (stderr + the result event's error text,
never the transcript — isaac-benp) is classified before any fallback:

- `limit-failure-re` -> `{:error :rate-limited :reason :wall :unavailable? true}`
- `cli-auth-failure-re` -> `{:error :auth-failed :reason :auth :unavailable? true}`
  ("Failed to authenticate", "OAuth session expired", /login, invalid api key)

`cli-weather` builds the classification, `invoke!` returns `(assoc result
:weather …)` instead of taking the fallback, and `chat*` / `stream-once` answer
with `weather-response`: the CLI's message (reset text included), no `:content`,
no second invocation. `provider-wall/normalize` already recognises both shapes,
so the drive suspends the turn on `:wall` / `:auth` and resumes it. MCP-init,
cli-start-failed and generic cli-error keep the fence fallback unchanged.

The fence (non-driven) path was deliberately left alone: it already classifies
auth, and widening `auth-failure-re` would deepen isaac-benp (it sniffs the
whole stdout).

**Files.** isaac-claude-code, branch `bean/isaac-2sxf`, commit **056eaf4**
- `src/isaac/llm/api/claude_cli.clj`
- `spec/isaac/llm/claude_driver_spec.clj` (+4 specs)
- `features/llm/api/claude_driver.feature` (+2 scenarios)

**Scenarios added.**
- `a session limit after the cycles have run is weather, not a fence fallback`
  — turn result "suspended", marker `reason :wall`, CLI invoked exactly once,
  no `:claude/driver-fallback`, no transcript entry containing "subtype".
- `a session limit on the very first invocation is weather too` (0 cycles).
- specs: limit after cycles, limit at cycle 0, expired OAuth, and a guard that
  the same words *in the transcript* are still a normal reply (isaac-benp).

**Test commands + counts** (in `isaac-claude-code-isaac-2sxf`):
- `bb spec` -> 88 examples, 0 failures, 3 pending (the @real smokes)
- `bb features` -> 55 examples, 0 failures
- `bb ci` -> both green
- red-first proof: reverting `src/` alone makes the 2 new scenarios fail
  ("expected suspended, got empty-terminal-response") and the 3 new specs fail.

**Cross-repo.** None — this bean is isaac-claude-code only. Branch pushed to
`origin/bean/isaac-2sxf`. Not landed, not tagged.

## Landed on main

main-sha: isaac-claude-code 056eaf4 (main tip 94a3bd6)

Planner check 2026-09-22: reran on the branch — claude-code `bb spec` 90/0 (3 @real pending), `bb features` 57/0, `bb ci` green with the agent pin moved to 52f29f7. Fast-forwarded to main; branches deleted. Not deployed: zanebot runs claude-code 34dbfa7.
