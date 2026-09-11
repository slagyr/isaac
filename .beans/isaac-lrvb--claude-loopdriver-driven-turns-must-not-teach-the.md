---
# isaac-lrvb
title: 'claude LoopDriver: driven turns must not teach the textual fence protocol; log MCP server status; fall back when the isaac MCP server fails'
status: completed
type: bug
priority: high
created_at: 2026-09-08T18:10:29Z
updated_at: 2026-09-08T18:53:49Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (`claude_cli.clj` `build-system-prompt` line ~175: adds `tool-protocol-contract` whenever the request has tools, driven or not). Child of isaac-tuk1; follow-up to isaac-6z4r (0.1.4).

## Evidence (zanebot, 2026-09-08 18:05Z, module 0.1.4 = 2a3b652)
Smoke `--model claude-cli` with one exec call: the driven spawn now reaches the model — `:claude/driver-exit :exit-code 0 :events {rate_limit_event 1, system 2, stream_event 9, assistant 1, result 1} :result-event true`, no fallback — but the reply was the literal text `<tool_call>{"name":"exec__run","arguments":{"command":"echo mcp-loop-ok"}}</tool_call>`, no toolCall/toolResult in the transcript, no tool executed. The system prompt handed to Claude Code (`--system-prompt`) contains the fence protocol contract, so Claude obeyed it instead of using MCP tools. Nothing logs whether the isaac MCP server connected or how many tools Claude Code discovered (the CLI's init event carries `mcp_servers` and `tools`). Rolled back to 0.1.3 (5b9d295) at 18:12Z.

## Required
1. On the driven path, `build-system-prompt` omits `tool-protocol-contract` (tools are native MCP). The fence contract stays for the fence/fallback path only.
2. Log `:claude/mcp-status :servers [...] :tools N` from the init event of every driven spawn.
3. If the init event reports the isaac server not connected (status ≠ connected) or zero tools while the turn has tools → `:claude/driver-fallback :reason :mcp-failed` and the fence path runs (extends nni3/0lyh fallbacks). Never proceed toolless on a turn that has tools.
4. A driven reply that still contains a fence (defence in depth) → execute nothing from it; treat as :mcp-failed fallback for that turn and log it.
5. The fake Claude Code: new fixture kind `mcp_status` (emits an init event with the given mcp_servers/tools); it must fail a tool_use row when the invocation's system prompt contains a fence contract? (optional) — at minimum the two scenarios below.

## Scenarios (@wip, planted isaac-claude-code 89e4704)
- a driven turn's system prompt carries no textual tool-call protocol
- an init event that reports the isaac MCP server failed falls back and logs the server status
Steps: existing (fake scripted with / invoked with, response is, transcript matching, log has entries matching). New steps: none; one new fixture kind (mcp_status).

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 13 scenarios green with @wip removed; `bb features && bb spec` green
- Field check by the verifier via exec on zanebot AFTER the planner pins the released version (not 0.1.3/0.1.4): `isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command'\''s output.'` → reply `mcp-loop-ok`; cli.log shows `:claude/mcp-status` with isaac connected and tools > 0, `:claude/driver-exit :result-event true`, NO `:claude/driver-fallback`; the session transcript has the exec toolCall/toolResult pair. The planner records the deploy on this bean and re-hails verify for the field check.
- Module version bump (0.1.5) + registry pin (train step)

## Handoff

branch: bean/isaac-lrvb @ 9d9ee24 (base origin/main@bfee7f5)

Worker **scrapper**@isaac-work-2. Module 0.1.5. Do not pin registry from this worker.

Driven `build-system-prompt` omits `tool-protocol-contract`. Fake CLI kind `mcp_status` emits a system/init event. Init with isaac status ≠ connected or zero tools on a turn that has tools, or a residual `<tool_call>` fence in the driven reply, logs `:claude/mcp-status` and `:claude/driver-fallback :reason :mcp-failed` then retries the fence path.

Acceptance: `bb features features/llm/api/claude_driver.feature` 13/13 green, `@wip` removed; `bb features` 27/27; `bb spec` 51 examples 0 failures (3 pending @real). Field check + registry pin remain planner/verifier after pin.



## Landed on main (2026-09-08)

main-sha: isaac-claude-code 9d9ee24ac39d8ae1b1451369e76b5d91d750c8b1

## Verify note (perceptor@isaac-verify, hail acb0b8b5)

Hermetic gates on origin/bean/isaac-lrvb @ 9d9ee24:
- bb features features/llm/api/claude_driver.feature → 13 examples, 0 failures, 51 assertions
- bb features → 27 examples, 0 failures, 99 assertions
- bb spec → 51 examples, 0 failures, 166 assertions, 3 pending @real
- Feature tamper: only @wip removed on the two planted lrvb scenarios; other 11 driver scenarios unchanged
- Module version 0.1.5 on the landed commit. Registry pin + field check are train/planner (bean: AFTER pin, not on 0.1.3/0.1.4). Live smoke not run.



## Deployed (2026-09-08 18:3xZ) — 0.1.5 (9d9ee24)
Smoke `--model claude-cli` one exec call → reply `mcp-loop-ok` with a toolResult in the transcript — but via the FALLBACK: `:claude/mcp-status :servers [{:name "isaac" …}] :tools 0` → `:claude/driver-fallback :reason :mcp-failed`. So this bean's contract holds (no fence taught, status logged, safe fallback) and the remaining blocker is the bridge itself: Claude Code cannot connect to `isaac mcp-bridge` for the turn. Left deployed (fallback answers correctly). Bridge failure being diagnosed by the planner.
