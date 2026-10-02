---
# isaac-izc1
title: 'claude-code: remove the fence fallback; a driven-mode failure is weather'
status: draft
type: bug
priority: high
created_at: 2026-10-02T04:41:33Z
updated_at: 2026-10-02T14:08:54Z
---

Found 2026-10-02 on yopp (agent ce1913c, claude-code ad434cb). A yopp ACP turn ended `:error :llm-error` on "You've hit your session limit · resets 5am (UTC)" and Isaac announced the provider broken; it was never parked/resumed.

claude-code recognizes that text (`limit-failure-re`, claude_cli.clj:369-372) and `cli-weather`/`weather-kind` (1032-1053) would classify it `{:unavailable? true :reason :wall}`, but only when `driven?` (line 1096). `driven?` is gated on the process-wide `defonce fail-mcp-init?*` (line 52), set by `fence-fallback!` on any MCP hiccup and never reset in production. The same session logged `:claude/driver-fallback :reason :mcp-failed` at 04:09:22Z — from then on, for every session in the process, no wall classification and no native tool calls (see the pseudo-tool-call bean).

## Wanted
- Driven-mode fallback is per-session or self-healing (retry driven mode next turn), never a permanent process latch.
- Wall classification (`cli-weather`) runs regardless of driven mode.
## Acceptance (scenarios TBD)
- After an unrelated MCP fallback, a CLI result reporting "session limit"/"resets 5am" (and the weekly variant) classifies as `:wall` with retry-at, parks the turn and auto-resumes — not `:llm-error`.

## Re-scope (Micah, 2026-10-02): remove the fallback and fence mode

The automatic driven→fence fallback should not exist: it swaps the reliable protocol (native tool calls over MCP) for a fragile one (hand-written tool-call text parsed back) exactly when something has gone wrong, hides the failure, and leaves a second mode nobody exercises to rot (weather, streaming and usage accounting already break in it). Nothing configures `:drives-tool-loop? false` (template default true; zanebot/yopp do not override; only one claude-code scenario uses it).

New intent:
- A driven-mode failure is provider weather: MCP not coming up, or the CLI erroring before a result, suspends the turn with reason `:mcp-unavailable` and retries in driven mode on the normal weather backoff. No process-wide switch.
- Usage limits / expired login classify as weather (`:wall` / `:auth`) unconditionally.
- Persistent MCP failure escalates: after repeated failed retries, an attention notice is raised (deliverable to gchat once isaac-ixcm lands).
- Remove fence mode entirely: `fail-mcp-init?*`, `fence-fallback!`, the fence retry, the `driven?` conditionals in `invoke!`, the textual tool-call contract and its parser, and the `:drives-tool-loop?` setting (clean cutover).
- Tool-less completions (episode gists/summaries on a claude-code model) keep working as plain completions without MCP.

Scenarios to be redrafted to this scope before baseline.
