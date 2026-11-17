---
# isaac-9sve
title: Claude Code children expose the OAuth token and system prompt in process listings
status: draft
type: bug
priority: high
created_at: 2026-11-17T02:23:19Z
updated_at: 2026-11-17T02:23:19Z
---

Found by Zane Lab, 2026-11-16 (security finding
`pgrep-prints-claude-oauth-token`). It happened for real that night: a lab
subagent ran `pgrep -fl`, the tool result carried the current token, and it
went into a model request and a transcript.

## What is wrong

The claude-code provider starts `claude --print …` children with
`CLAUDE_CODE_OAUTH_TOKEN` in their environment and the whole system prompt
(soul, rules, tool list) in argv. On macOS `pgrep -fl` and `ps eww` print
both. Any agent that lists processes while debugging receives a live token
as tool output and sends it to its model.

## Direction

- Do not leave the token in the child's environment for its lifetime: hand
  it over through an `apiKeyHelper` or a mode-600 settings file removed after
  start, or on stdin at startup.
- Pass the system prompt by file (`--system-prompt-file`), not argv.

## Acceptance (to be written as scenarios)

With a canary token configured, the process listing of a running driven
turn's child shows neither the token nor the prompt.

Draft: the hand-over mechanism needs a spike against the real CLI before
scenarios can be written (which of the options the CLI supports in driven
mode).
