---
# isaac-ia5
title: "Handle isaac --help and -h as top-level flags"
status: completed
type: bug
priority: low
created_at: 2026-04-11T23:48:14Z
updated_at: 2026-04-11T23:53:46Z
---

## Description

When the user runs 'isaac --help' or 'isaac -h' without a subcommand, Isaac should print usage instead of reporting 'Unknown command: --help'.

## Current behavior
$ isaac --help
Unknown command: --help
Usage: isaac <command> [options]
...

The fall-through in main.clj/run treats --help as an unknown command and exits with code 1 after printing usage. The usage still appears, but the error message is confusing and the exit code is wrong.

## Fix
In src/isaac/main.clj, update the cond in 'run' to recognize --help and -h as no-subcommand cases that print usage and exit 0:

  (or (nil? cmd) (str/blank? cmd) (= "--help" cmd) (= "-h" cmd))
  (do (println (usage)) 0)

## Feature scenarios
features/cli/cli.feature has two @wip scenarios:
- 'Top-level --help flag shows usage' (line 35)
- 'Top-level -h flag shows usage' (line 42)

## Acceptance
Remove @wip and verify each passes:
  bb features features/cli/cli.feature:35
  bb features features/cli/cli.feature:42

Full suite: bb features and bb spec pass.

