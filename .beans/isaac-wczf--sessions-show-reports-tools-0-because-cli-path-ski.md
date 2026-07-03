---
# isaac-wczf
title: sessions show reports Tools 0 because CLI path skips tool registration
status: todo
type: bug
priority: normal
tags:
    - cli
    - sessions
    - tools
created_at: 2026-07-03T15:35:26Z
updated_at: 2026-07-03T15:35:26Z
---

## Problem
`isaac sessions show <id>` always reports `Tools 0` (or the number of tools registered in the current CLI process).

This is because:
- `status-data*` does `(count (tool-registry/all-tools))`
- The `sessions` CLI path (`install-cli!`) only loads config + session store; it never activates modules or runs the `:isaac.agent/tools` berths.
- Tool registration only happens in a full agent/server boot.

The count should reflect the tools available to that session/crew (or at least be accurate when queried from a running agent context).

## Scope
- `isaac-agent/src/isaac/bridge/status.clj`
- `isaac-agent/src/isaac/session/cli.clj`
- Tool registry activation paths
- Related specs/features that assert tool count

## Acceptance Criteria
- When running inside a live agent (full module load), `sessions show <id>` (or /status) reports the number of tools the session can actually use.
- `status-data` can take the session/crew context and compute a meaningful count (e.g. using allowed tools for the crew or the global registry when in full context).
- Bare CLI `sessions show` either loads the relevant crew's tools for the count, or clearly indicates it's showing process-registered tools.
- The "Tools" line in the status output is useful and not misleading.
- Existing tests still pass or are updated; add a test that exercises the count from a crew context.
- No regression in lightweight CLI paths for other fields.

## Notes
Observed in `isaac-work-1` / `isaac-work-2` on zanebot while debugging session state. The session had many tools available in its actual agent context.
