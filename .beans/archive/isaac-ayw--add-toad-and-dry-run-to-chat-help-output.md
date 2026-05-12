---
# isaac-ayw
title: "Add --toad and --dry-run to chat help output"
status: completed
type: bug
priority: low
created_at: 2026-04-12T00:05:28Z
updated_at: 2026-04-12T00:07:00Z
---

## Description

The chat command's registry entry in src/isaac/cli/chat.clj lists only --agent, --model, --resume, --session in its :options. But --toad and --dry-run are supported by the parser and run-fn (from isaac-0wx). Running 'isaac help chat' omits them, making the flags invisible to users even though they work.

This is a classic symptom of the split source-of-truth: the parser knows, the run-fn knows, but the help output doesn't.

## Fix
Add --toad and --dry-run entries to the :options list in chat.clj:

  ["--toad"     "Launch Toad TUI via ACP"]
  ["--dry-run"  "Print the Toad launch command without spawning"]

## Feature scenario
features/cli/cli.feature (@wip) 'help chat lists all registered chat options' verifies that 'isaac help chat' includes all chat flags by name. Fails today, passes after the fix.

## Acceptance
Remove @wip from 'help chat lists all registered chat options' and verify:
  bb features features/cli/cli.feature:25

Manual: 'isaac help chat' shows --toad and --dry-run in the Options list.
Full suite: bb features and bb spec pass.

