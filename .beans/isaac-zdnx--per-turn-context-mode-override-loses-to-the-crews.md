---
# isaac-zdnx
title: Per-turn context-mode override loses to the crew's :context-mode (--with-crew and --with-model do win)
status: in-progress
type: bug
priority: normal
tags:
    - agent
    - cli
created_at: 2026-09-22T21:04:40Z
updated_at: 2026-09-22T21:15:36Z
---

## Observed (2026-09-22, zanebot, agent 0.1.81)

`isaac prompt -s isaac-work-2 --with-context-mode full -m "…"` on a session
whose crew (scrapper) sets `:context-mode :reset` resolved to
`:session/behavior-resolved :context-mode :reset` and skipped compaction with
`:reason :context-reset`. The same prompt with `--with-crew main` (a crew with
no context-mode) resolved `:full`. So the per-turn override is honoured only
when the crew is silent; a crew value beats it. `--with-model` and `--with-crew`
do win per turn, so this is inconsistent with the other `--with-*` overrides
(isaac-4e4b's stated intent: uniform override across hail/prompt/acp/chat).

## Expected

Per-turn overrides (`--with-context-mode`, hail `:with-context-mode`) take
precedence over crew config for that turn, same as `--with-model`.

## Acceptance

- Scenario: a crew with `:context-mode :reset`; a prompt turn with
  `--with-context-mode full` builds the request from the full transcript and
  logs `behavior-resolved :context-mode :full`.
- Scenario: no override → crew value applies (unchanged).
- `bb spec` / features green in isaac-agent.
