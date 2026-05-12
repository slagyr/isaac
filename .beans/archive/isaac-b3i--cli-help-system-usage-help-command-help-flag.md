---
# isaac-b3i
title: "CLI help system - usage, help command, --help flag"
status: completed
type: task
priority: high
created_at: 2026-04-01T14:57:30Z
updated_at: 2026-04-01T15:03:29Z
---

## Description

Implement root CLI dispatcher with help per features/cli.feature.

## Changes to isaac.main
- No args: print usage with command list, exit 0
- Unknown command: print error + usage, exit 1
- help <command>: print that command's help, exit 0
- help <unknown>: print error, exit 1
- <command> --help: same as help <command>

## Command Registration
Commands should self-register their help text so cli.feature doesn't need to know about all commands. Each command provides a usage string and options list.

## Exit Codes
0 for success, 1 for errors (unknown command, etc.)

