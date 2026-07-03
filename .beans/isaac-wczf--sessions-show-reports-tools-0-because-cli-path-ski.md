---
# isaac-wczf
title: sessions show reports Tools 0 because CLI path skips tool registration
status: in-progress
type: bug
priority: normal
tags:
    - cli
    - sessions
    - tools
created_at: 2026-07-03T15:35:26Z
updated_at: 2026-07-03T16:24:15Z
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
- `status-data*` (and callers) resolves the crew for the session and uses the crew's `:tools :allow` list (from loaded config) to compute `(tool-registry/all-tools allowed-tools)`.
- In the `sessions` CLI `run-show`, after `install-cli!`, the code resolves the target session's crew, gets its allow list, ensures/activates the relevant tools (via existing `activate-missing-tool!` or `tool-definitions` path with the module index), then reports the correct filtered count.
- `sessions show <id>` (CLI or /status in live agent) reports the number of tools allowed for that session's crew.
- The count is consistent with what the prompt builder / turn code would use for the same crew.
- Bare CLI invocations now produce a non-zero, useful number when the crew has tools configured.
- Existing tests pass or are updated; add coverage for CLI `sessions show` producing the crew-specific tool count.
- No regression for lightweight CLI paths or sessions without a tools section (should report 0 or appropriate value).

## Notes
Observed in `isaac-work-1` / `isaac-work-2` on zanebot while debugging session state. The session had many tools available in its actual agent context.

## Suggested Approach
- In `bridge/status.clj`:
  - Enhance `status-data*` to derive `allowed-tools` from the crew in `ctx` or the loaded config (e.g. `(get-in ctx [:crew :tools :allow])` or resolve via `cfg`).
  - Change the line to: `:tool-count (count (tool-registry/all-tools allowed-tools))`
- In `session/cli.clj` `run-show`:
  - After `(let [{:keys [root store] :as install} (install-cli! opts) ...]`
  - Resolve the session, its crew, and `allowed` from the config.
  - If needed, load a minimal module-index or call activation: `(tool-registry/tool-definitions allowed module-index)` or equivalent to populate registry for the allow list.
  - Build ctx with the allowed info and call `(bridge/status-data session-id (assoc ctx :allowed-tools allowed ...))` or pass through.
- Reuse existing logic from `turn.clj` (`allowed-tool-names`) and `registry.clj` (`activate-missing-tool!`, `all-tools` arity).
- Update `format-status` caller if the data shape changes (probably not).
- Tests: extend bridge spec and add CLI-specific test for `sessions show` with a crew that has `:tools :allow`.

This keeps the CLI path lightweight while ensuring the reported count matches what the live session would have.
