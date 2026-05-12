---
# isaac-1ktq
title: "Tool dependencies: degrade gracefully instead of hard-failing server startup"
status: completed
type: bug
priority: normal
created_at: 2026-04-22T21:50:21Z
updated_at: 2026-04-22T21:57:16Z
---

## Description

src/isaac/tool/builtin.clj:601 throws when rg is not on PATH, which prevents 'isaac server' from starting at all — even if no crew is configured to use grep.

Behavior change:
- Missing tool dependency (e.g., rg for grep): log a warn-level event (:tool/register-skipped with :tool and :reason fields) and skip that tool's registration. Server starts normally.
- Other tools with external deps: same pattern. Each tool's register-* fn performs its own dependency check and degrades gracefully.
- Crew that attempts to call a skipped tool: tool-registry returns a clear 'tool X is not available: rg not installed' error at invocation time, rather than silently missing from the registry (though the latter would also be acceptable).

Alternative (rejected): register the tool anyway, check deps on first invocation. Trades startup visibility for late errors. The warn-log-at-startup approach surfaces the issue up-front without blocking.

Encountered on zanebot: 'isaac server' died with 'rg not found on PATH' because ripgrep wasn't installed. Expected: warn + continue.

Applies to any tool with external binary requirements. Unit spec can exercise the pattern generically.

