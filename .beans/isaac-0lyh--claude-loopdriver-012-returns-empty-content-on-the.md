---
# isaac-0lyh
title: 'claude LoopDriver 0.1.2 returns empty content on the real CLI: consume the 2.1 stream shapes, log the CLI exit, fall back on error results'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-08T16:38:14Z
updated_at: 2026-09-08T17:05:44Z
parent: isaac-tuk1
---

Repo: isaac-claude-code. Child of isaac-tuk1; follow-up to isaac-nni3 (whose fix was green on the fake CLI and failed on the real one, 2026-09-08 16:29Z, rolled back to 0.1.0).

## Evidence
- Smoke on zanebot (Claude Code 2.1.236, agent 0.1.50, module 0.1.2 = 79c7946): `:turn/loop-driver :driver :provider`, two `:claude/title-side-call` lines 1.3 s apart (two ~1 s CLI spawns), no `:claude/driver-fallback`, then `:chat/response-failed :error :empty-terminal-response`. The driver produced no content and did not classify the exit.
- Probe of the real CLI from inside the server (keychain available), driver flags minus --mcp-config: exit 0, stderr empty, events: 7 stream_event (content_block_delta / text_delta), 4 message, 2 text, 1 system/init, 1 system/status, 1 result `{is_error:false, stop_reason:end_turn, num_turns:1, usage:{input_tokens:2, cache_creation_input_tokens:5473, cache_read_input_tokens:3289, output_tokens:4}}`. The reply text is only in text_delta chunks and the trailing message events.
- With --mcp-config (the smoke path) the spawn died in ~1 s: most likely an error result (MCP server failed to start/connect under --strict-mcp-config) that the driver treated as 'no content' instead of a fallback trigger. Unverified — the driver logs nothing about the process exit, which is the first thing to fix.

## Required
1. Log `:claude/driver-exit :exit-code N :result-event bool :stderr <head> :events {type counts}` for EVERY spawn.
2. Assemble the reply from stream_event/content_block_delta/text_delta and message events; usage from the result event (last-input-tokens = input + cache_read + cache_creation, per vuto/5xn7).
3. A result event with is_error, or an exit with no result event, → fence-path fallback with `:claude/driver-fallback :reason :cli-error` and the stderr/result text logged (extends nni3's cli-start-failed).
4. The fake Claude Code emits the REAL shapes above (fixture extension: kinds text_delta and error_result), so `bb features` exercises the parser the way 2.1 speaks.

## Scenarios (@wip, planted isaac-claude-code f68aff0 in features/llm/api/claude_driver.feature)
- text that arrives only as content_block_delta stream events becomes the reply
- a result event with is_error, or an exit with no result event, falls back with the CLI's stderr logged
Steps: all existing (fake Claude Code scripted with / invoked with, the response is, transcript matching, sessions match, log has entries matching). New steps: none; two new fixture kinds.

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 9 scenarios green with @wip removed (incl. the 7 existing)
- `bb features && bb spec` green
- REAL-BINARY smoke run by the VERIFIER via its exec tool on zanebot (the verify session runs inside the server, keychain available): `isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command'\''s output.'` → `mcp-loop-ok`, and cli.log shows `:turn/loop-driver :driver :provider` and `:claude/driver-exit :exit-code 0 :result-event true` with NO `:claude/driver-fallback`. A bean cannot pass without this line.
- Module version bump (0.1.3) + registry pin (train step)

## Implementation notes

branch: bean/isaac-0lyh @ 5b9d295 (base origin/main@07a821c)

Parser consumes Claude Code 2.1 `stream_event`/`content_block_delta`/`text_delta` plus `message` events; usage from the `result` event. Every driven spawn logs `:claude/driver-exit`. `is_error` or missing result → fence fallback `:cli-error`. Fake CLI kinds `text_delta` and `error_result`. Module version 0.1.3 (registry pin is the train step — not done here).

`bb features` 23/0/81; `bb spec` 41/0/119 (3 pending @real). Driver feature: 9 scenarios green, @wip removed. Real-binary smoke is verifier-owned.
