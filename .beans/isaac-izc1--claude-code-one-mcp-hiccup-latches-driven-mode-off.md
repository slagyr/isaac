---
# isaac-izc1
title: 'claude-code: one MCP hiccup latches driven mode off process-wide, so usage limits stop being walls'
status: draft
type: bug
priority: high
created_at: 2026-10-02T04:41:33Z
updated_at: 2026-10-02T04:41:33Z
---

Found 2026-10-02 on yopp (agent ce1913c, claude-code ad434cb). A yopp ACP turn ended `:error :llm-error` on "You've hit your session limit · resets 5am (UTC)" and Isaac announced the provider broken; it was never parked/resumed.

claude-code recognizes that text (`limit-failure-re`, claude_cli.clj:369-372) and `cli-weather`/`weather-kind` (1032-1053) would classify it `{:unavailable? true :reason :wall}`, but only when `driven?` (line 1096). `driven?` is gated on the process-wide `defonce fail-mcp-init?*` (line 52), set by `fence-fallback!` on any MCP hiccup and never reset in production. The same session logged `:claude/driver-fallback :reason :mcp-failed` at 04:09:22Z — from then on, for every session in the process, no wall classification and no native tool calls (see the pseudo-tool-call bean).

## Wanted
- Driven-mode fallback is per-session or self-healing (retry driven mode next turn), never a permanent process latch.
- Wall classification (`cli-weather`) runs regardless of driven mode.
## Acceptance (scenarios TBD)
- After an unrelated MCP fallback, a CLI result reporting "session limit"/"resets 5am" (and the weekly variant) classifies as `:wall` with retry-at, parks the turn and auto-resumes — not `:llm-error`.
