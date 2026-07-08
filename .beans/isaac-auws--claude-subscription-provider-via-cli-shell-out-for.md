---
# isaac-auws
title: claude subscription provider via cli shell-out for raw completions
status: todo
type: feature
priority: normal
tags:
    - subscription
    - claude
created_at: 2026-07-08T21:44:45Z
updated_at: 2026-07-08T21:44:51Z
---

## Problem
Isaac needs a first-class way to drive the Claude Code / Max subscription (via logged-in claude CLI) for raw request/response completions. The current "claude-code" hack (type:anthropic + oat token as api-key) fails auth. Direct HTTP with the subscription token is rejected as invalid x-api-key.

## Goal
A "claude-code" (or claude) provider that execs the claude binary with raw full-prompt text each turn. Isaac owns the transcript, tool loop, and sessions. The binary is invoked with:
- --print
- --output-format text|stream-json
- --disallowed-tools all
- --no-session-persistence
- full prompt text (system + history) constructed by Isaac
- no --resume/--continue

The claude binary must be logged in (subscription) for the process user; no raw ANTHROPIC_API_KEY is required or used.

## Scenarios (9)
1. Basic non-tool prompt uses raw shell-out
2. Streaming response
3. Tool-using turn (Isaac-managed tools via serialized prompt)
4. Error from binary is reported
5. Custom binary path
6. Extra args from provider config forwarded
7. Full history passed each turn (Isaac controls transcript)
8. Uses subscription login (no ANTHROPIC_API_KEY)
9. Config with custom binary + extra args

## Acceptance
- All 9 scenarios pass (use the two new consolidated steps: claude binary stub + invocation assertion)
- New steps kept to minimum (2)
- No claude tools or claude session management
- Raw like the API; Isaac owns everything
- Works on a box with `claude /login` done (uses .credentials.json)

## Files
- isaac-agent/features/llm/api/claude_cli.feature (wip)
- New provider impl in isaac-agent (claude-cli api)
- Update manifest for claude-code template
- Docs / examples if needed

## Notes
- Overhead ~3-6s per turn (full non-bare CLI for login)
- Use --bare only if we can still hit subscription (current tests show it forces key)
- For tool roundtrips: serialize tool defs + results as text in the prompt; parse model output for TOOL_CALL if needed (or keep simple text-only first)
- Follows terms: we literally exec the official logged-in client

See feature for exact Gherkin.
