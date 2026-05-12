---
# isaac-e793
title: "Audit existing scenarios and add log assertions for mutations"
status: completed
type: task
priority: low
created_at: 2026-04-19T23:13:07Z
updated_at: 2026-04-19T23:24:43Z
---

## Description

Logging is a crosscutting concern. Mutations or notable state transitions that aren't asserted in scenarios can silently stop emitting log entries without a test catching it.

## Scope
Walk every feature file under features/ and for each scenario that performs a mutation, tool call, CLI command, or lifecycle event, ensure there's an `the log has entries matching:` assertion covering the expected log entry.

## Mutation classes worth logging
- CLI commands that write state (isaac config set/unset, isaac chat turns, session writes)
- Tool calls (read/write/edit/exec results)
- Compaction events (already done for some — see features/context/compaction.feature as a model)
- ACP session events (start/resume/end)
- Auth events (login/logout/refresh)
- Provider errors (as surfaced to the user)

## Approach
1. Grep features/ for 'the log has entries matching:' to see what's already asserted
2. Walk each feature file; for each Scenario, identify mutations; add log assertions where missing
3. Establish event-keyword conventions in one place (docs or a short style note in CLAUDE.md or similar) — e.g., :config/set, :session/compaction-started, :tool/exec, :auth/login
4. For scenarios that intentionally test the no-log path (e.g. reads), note that explicitly

## Scope decisions for the worker
- Do NOT add assertions for every debug-level trace; only info and above are spec-worthy
- New scenarios drafted after this bead should follow the convention (no retrofit needed)

## Background
Observed 2026-04-19 while drafting isaac-43lr (config set/unset): noticed the set/unset scenarios had no log assertions, and the same gap exists across most of the feature suite.

## Acceptance Criteria

1. Every scenario that performs an observable mutation or lifecycle event has a log assertion (or a comment explaining why it doesn't)
2. A short style note in CLAUDE.md (or similar) documents event-keyword conventions
3. bb features passes
4. bb spec passes

