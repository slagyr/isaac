---
# isaac-hi5n
title: CLI and core formatters must emit plain text (MD wrapping only in comms)
status: completed
type: bug
priority: normal
tags:
    - cli
    - formatting
    - comm
created_at: 2026-07-03T15:35:26Z
updated_at: 2026-07-03T19:55:11Z
---

## Problem
Core formatters (e.g. `format-status`) and the bridge layer were unconditionally adding markdown fences (` ```text `) around human-readable output like session status. This leaked presentation concerns into the "ground level".

CLI (`isaac sessions show`, etc.) must emit raw plain text.
Tool results fed to LLMs must stay clean.
Only comm/presentation layers (ACP, Discord, chat renderer, etc.) should decide whether to wrap for their client.

The recent removal from `format-status` was a start, but the layering isn't complete or documented.

## Scope
- `isaac-agent/src/isaac/bridge/status.clj`
- `isaac-agent/src/isaac/bridge/core.clj` (reply handling)
- `isaac-agent/src/isaac/session/cli.clj`
- Comm implementations (acp, discord, ...)
- Related features and specs

## Acceptance Criteria
- `format-status` (and similar core formatters) always return plain text with no markdown.
- CLI `sessions show` (and list where applicable) produces raw aligned text.
- /status slash replies and equivalent go through as plain text at the bridge level.
- No ```text fences appear in raw `isaac` CLI output.
- ACP and other comms can optionally wrap for their clients (no forced wrapping in core).
- Feature tests updated (e.g. the one claiming "/status prints ... as markdown table").
- A small convention or tagging mechanism exists so comms know when something is a "preformatted block" without guessing.
- Clear comment or short doc in the relevant bridge file explaining the rule: "plain at ground level; wrapping is a comm concern".

## Notes
See conversation around 2026-07-03 about sessions show and session_info tool output.
