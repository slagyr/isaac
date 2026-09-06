---
# isaac-nni3
title: 'claude LoopDriver: real CLI rejects stream-json without --verbose and the driver does not fall back'
status: draft
type: bug
priority: high
created_at: 2026-09-06T02:02:10Z
updated_at: 2026-09-06T02:06:13Z
---

Repo: isaac-claude-code (features/llm/api/claude_driver.feature). Found on the isaac-5xn7 deploy smoke, 2026-09-06 02:00Z, zanebot, Claude Code 2.1.236.

## Evidence
`isaac prompt --crew scrapper --model claude-cli --session train-mcp-smoke '…echo mcp-loop-ok…'` → the turn errors with the CLI's own message: `Error: When using --print, --output-format=stream-json requires --verbose`. The driver spawns `claude -p … --output-format stream-json` without `--verbose`; the process exits non-zero before emitting any stream event; the turn fails instead of falling back to the fence path (isaac-5xn7 decision 4: an older CLI or a failed MCP init falls back for that turn, logged `:claude/driver-fallback`; never fail the turn for it). The module's fake Claude Code does not enforce the real CLI's flag rules, so `bb features` was green.

## Required
1. Pass `--verbose` whenever `--output-format stream-json` is used with `-p` (real CLI contract, 2.1.x).
2. A CLI process that exits before its first stream-json event (any non-zero exit, stderr captured) is a fallback trigger: log `:claude/driver-fallback :reason :cli-start-failed` with the stderr, run the turn on the fence path.
3. The fake Claude Code gains the real rule: without `--verbose` it exits 1 with the exact message above, so the suite catches this class.

## Status
0.1.1 (14b9ef2) is pinned in the registry and live on zanebot; rollback to 0.1.0 (597c818) pending (`modules upgrade` did not apply the downgrade — see planner notes). Scenario proposal pending Micah's review.



Rollback done 02:12Z: registry back to 597c818, zanebot upgraded + restarted, `--model claude-cli` pong OK on the fence path. Registry note: `isaac modules upgrade` read a cached registry for ~2–3 min after the push ("up to date"); retry until `modules list` shows the target sha.
