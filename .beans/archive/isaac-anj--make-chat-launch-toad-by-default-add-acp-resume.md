---
# isaac-anj
title: "Make chat launch Toad by default, add acp --resume"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T01:39:53Z
updated_at: 2026-04-13T02:10:36Z
---

## Description

## Changes

### chat command becomes Toad by default
- `isaac chat` launches Toad (previously required `--toad`)
- Remove `--toad` flag — it's the only behavior now
- Remove the old REPL loop in `chat/loop.clj`
- All flags (`--agent`, `--model`, `--resume`, `--session`, `--remote`) pass through to the `isaac acp` subprocess
- `--dry-run` still prints the command without spawning

### acp --resume
- `--resume` finds the most recent session for the agent and attaches to it
- `--resume --agent ketch` filters by agent
- If no session exists, creates a new one
- `--resume --model` is rejected as ambiguous (exit 1 with error)
- Session lookup uses the same `find-most-recent-session` logic from the old chat loop

### Code to remove
- `src/isaac/cli/chat/loop.clj` — the interactive REPL loop (prompt-for-input, chat-loop)
- `features/chat/options.feature` — scenarios for the old chat flow (resume, session, model override via chat)
- The `prepare` function in loop.clj may still be useful for session resolution — extract what's needed before deleting

## Acceptance

- `bb features features/cli/acp-resume.feature` passes with @wip removed (4 scenarios)
- `bb features features/chat/toad.feature` passes with @wip removed (8 scenarios)
- @wip removed from all 12 scenarios
- Old REPL code removed
- `isaac chat` launches Toad without `--toad` flag

## Acceptance Criteria

All 12 @wip scenarios pass with @wip removed. Old REPL loop removed. isaac chat launches Toad by default.

