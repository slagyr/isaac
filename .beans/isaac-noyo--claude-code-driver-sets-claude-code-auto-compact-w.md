---
# isaac-noyo
title: claude-code driver sets CLAUDE_CODE_AUTO_COMPACT_WINDOW from the model's context-window × compaction threshold so a driven turn compacts at Isaac's budget
status: scrapped
type: feature
priority: high
tags:
    - claude-code
    - tokens
created_at: 2026-09-22T23:51:45Z
updated_at: 2026-09-22T23:55:37Z
---

## Why

In driven mode the claude CLI runs the tool loop and Isaac defers compaction to
between turns (isaac-1sdl). The CLI compacts on its own only near its window,
and Opus 5 in Claude Code runs at 1M with a default auto-compact point near
967K. The isaac-ruom turn on 2026-09-22 (80 requests, 79 tool cycles, 25 min)
grew its context to an estimated 600–850k tokens and re-read it every request:
roughly 9–10M prompt tokens, the cost quadratic in cycles. Reset mode fixed the
turn's start, not its middle.

Claude Code exposes the knob: `CLAUDE_CODE_AUTO_COMPACT_WINDOW=<tokens>`
(100K–1M, takes precedence over flag and setting; docs: model-config "Set the
auto-compact window"). NOT yet set on zanebot: the planner's classifier refuses edits to the provider
files (they carry OAuth tokens). Micah adds `:CLAUDE_CODE_AUTO_COMPACT_WINDOW
"160000"` (= 0.8 × the 200k working budget) to the `:env` map of
`config/providers/micah-claude.edn` and `tono-claude.edn`; both hot-reload.

## Change (isaac-claude-code driver)

- When spawning the CLI for a model whose entry declares `:context-window`, set
  `CLAUDE_CODE_AUTO_COMPACT_WINDOW` to `threshold × context-window` (the same
  compaction threshold the drive resolves for the session, default 0.8) unless
  the provider's `:env` already sets it (explicit config wins, per isaac-12fo).
- Log the chosen window once per turn at debug on `:claude/driver-exit` or
  the invocation line.
- The CLI caps the value at the model's real window; below 100K it is
  rejected — clamp to 100000 and warn once.

## Scenarios (claude-code features, fake CLI captures env)

- model with `:context-window 200000`, default threshold → the CLI env carries
  `CLAUDE_CODE_AUTO_COMPACT_WINDOW=160000`.
- provider `:env` sets it explicitly → that value is passed, untouched.
- model without `:context-window` → the variable is not set.

## Acceptance

- `bb spec` / `bb features` / `bb ci` green in isaac-claude-code.
- One-time on zanebot after deploy: a driven work turn longer than ~40 cycles
  shows the CLI compacting (the cycle stamps from isaac-ewxh's follow-up, or
  the driver-exit sums, stay bounded near the window instead of climbing).

## Related

isaac-1sdl (compaction deferred in driven loops), isaac-ewxh, isaac-dgod,
isaac-3ueo (CLI overhead), the ruom burn note on isaac-ruom.

## Scrapped (Micah, 2026-09-22)

"I am fine with Claude CLI running its full context. There is no need to configure it otherwise." No CLAUDE_CODE_AUTO_COMPACT_WINDOW on any provider or model; the CLI compacts at its own window.
