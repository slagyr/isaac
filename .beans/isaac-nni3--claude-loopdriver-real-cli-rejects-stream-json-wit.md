---
# isaac-nni3
title: 'claude LoopDriver: real CLI rejects stream-json without --verbose and the driver does not fall back'
status: in-progress
type: bug
priority: high
created_at: 2026-09-06T02:02:10Z
updated_at: 2026-09-08T15:33:07Z
parent: isaac-tuk1
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



## Scenario (approved 2026-09-06, Micah) — planted at isaac-claude-code 958d166
`features/llm/api/claude_driver.feature:157` — a CLI that exits before its first stream event falls back to the fence path with the stderr logged.

## Step ledger
| Step | Status |
|---|---|
| Given a fake Claude Code on the path scripted with: | existing (5xn7) |
| **Given the fake Claude Code exits {code:int} before streaming with stderr {text}** | **NEW** — the existing failure step scripts a failed MCP init after the process is up; this scripts death at startup, the class the real CLI produced |
| When the user sends … / Then the response is … / the log has entries matching: | existing |
| Then the fake Claude Code was invoked with: | existing (5xn7); asserted twice — driver attempt carried --verbose, retry went through the fence path |

## Acceptance
- [ ] `bb features features/llm/api/claude_driver.feature:157` green with @wip removed; the other 6 driver scenarios and claude_cli.feature unchanged
- [ ] `bb features && bb spec` green in isaac-claude-code
- [ ] Real-binary smoke on zanebot after the train: `isaac prompt --crew scrapper --model claude-cli --session train-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command's output.'` → `mcp-loop-ok`, and the log shows `:turn/loop-driver :driver :provider` with NO `:claude/driver-fallback`
- [ ] Module version bump (0.1.2) + registry pin (train step)
