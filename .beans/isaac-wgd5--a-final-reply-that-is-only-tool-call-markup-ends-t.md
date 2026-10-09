---
# isaac-wgd5
title: A final reply that is only tool-call markup ends the turn as the answer
status: scrapped
type: bug
priority: normal
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T14:43:02Z
---

Found 2026-10-02 on skiff: the 04:24:08Z assistant reply was the text `=\n<arguments>{\"command\":\"sed -n '420,480p' …\"}</arguments>` with :end-turn, so the turn ended with that as skiff's answer; the same command ran properly after the user asked "Are you stuck?". Context was ~1M tokens at the time (see the compaction bean) and driven mode was latched off (see the claude-code MCP latch bean).

claude-code's `call-shape-re` (claude_cli.clj:288-289) only knows `<tool_call`/`<invoke`/`<function_calls`/fenced JSON, so a bare `<arguments>` slips through as visible content. isaac-agent turn.clj already has `guard-empty-terminal-response` (830-861) that re-prompts once on a bad terminal response.

## Wanted
A sibling guard: an :end-turn reply whose content is tool-call-shaped markup (`<arguments>`, `<invoke`, `<tool_call`, …) is logged and nudged to retry once; a repeat failure surfaces as an explicit error, not as the reply.
## Acceptance (scenarios TBD)
- Such a reply triggers one corrective re-prompt; a second identical failure ends the turn with an explicit error.

## Scrapped (Micah, 2026-10-02)

Moot once isaac-izc1 removes fence mode (the <arguments> slip came from the text tool-call path). Reopen only if a markup-only reply shows up in driven mode.
