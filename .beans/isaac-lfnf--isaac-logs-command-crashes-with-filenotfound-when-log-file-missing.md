---
# isaac-lfnf
title: isaac logs command crashes with FileNotFound when the log file does not exist
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-27T15:45:00Z
updated_at: 2026-06-27T16:39:38Z
---

## Summary
`isaac logs -f` (and plain `isaac logs`) fails with a `java.io.FileNotFoundException` for the default log path (`/tmp/isaac.log`) when that file has never been written.

## Reproduction
On zanebot (or any system without a prior log file at the default location):
```sh
isaac logs -f
```
(especially right after install or when using launchd-wrapped services)

## Observed stack
```
java.io.FileNotFoundException
Message:  /tmp/isaac.log (No such file or directory)
Location: .../isaac/logs/cli.clj:44
...
isaac.log-viewer/tail!
    (RandomAccessFile. file "r")
```

## Root cause
- Hard-coded default in `isaac.logger` atom: `:log-file "/tmp/isaac.log"`
- `isaac.logs.cli/run` always falls back to `(log/log-file)` and passes it straight to `viewer/tail!`
- `isaac.log-viewer/tail!` does `(java.io.RandomAccessFile. (java.io.File. path) "r")` with no existence check or creation
- No user-friendly message or graceful "file does not exist yet" behavior for the `logs` command
- User config only partially supports logging (`[:log :output]` is read; no `:file` key is used to override the path)
- On Homebrew + launchd setups the real logs often live under `~/Library/Logs/isaac/...` or per-service paths, so `/tmp/isaac.log` is never created

## Expected behaviour
- `isaac logs` (with or without `-f`) should not crash.
- When the target file is absent it should either:
  - Create an empty file (so tail works), or
  - Print a clear message ("No log entries yet at <path>. Run an isaac command that produces logs first, or use --file PATH."), and for `-f` optionally wait for the file to appear.
- Prefer a configured log file from `config/isaac.edn` if present.
- Reasonable default location (under the isaac root or `~/.isaac/logs/isaac.log` instead of `/tmp`).

## Acceptance criteria
- `isaac logs` and `isaac logs -f` succeed (or give friendly output) even when the log file does not exist.
- `log-viewer/tail!` (and callers) handle a missing file without exception; support creation or waiting when `:follow?` is true.
- Logs CLI reads a `:log {:file "..."}` (or equivalent) from user config and uses it.
- Update/add specs in `isaac-foundation/spec/isaac/logs/cli_spec.clj` (and log_viewer if unit-testable).
- Change the hard-coded default away from `/tmp/isaac.log` (or document why /tmp is intentional and make the CLI robust).
- Bonus: `isaac logs` without a log file prints the path it would watch and how to generate entries.


## Implementation (work-2)
Repo: isaac-foundation @ f9be40b

- `log-viewer/tail!` prints a friendly message when the log file is missing; with `:follow?` true it waits for the file then tails.
- `logs/cli` reads `:log :file` (preferred) or `:log :output` from config; default path is `<root>/logs/isaac.log`.
- Logger default log file moved from `/tmp/isaac.log` to `~/.isaac/logs/isaac.log`.
- Specs + feature scenarios added for missing-file behavior and `:log :file` config.

Verification: `bb spec` (780 examples, 0 failures); `bb jvm-features features/logs/cli.feature` (17 examples, 0 failures).
