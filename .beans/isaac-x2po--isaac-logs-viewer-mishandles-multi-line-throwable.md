---
# isaac-x2po
title: isaac logs viewer mishandles multi-line throwable / error entries
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-29T14:51:00Z
updated_at: 2026-06-29T17:00:00Z
---

isaac logs -> isaac.log-viewer/tail! -> format-line is LINE-ORIENTED: it EDN-parses one line at a time (format-entry dissocs known keys, renders the rest). Multi-line :throwable #error{...} blocks are a partial EDN form per line -> parse fails -> raw fallback, and the throwable's continuation lines render raw too. Result: error entries with throwables don't format (and may desync surrounding lines).

Reported by Micah: isaac logs doesn't format the scheduler 'Output closed' errors. Single-line scheduler entries SHOULD format, so confirm live on zanebot whether it's the throwable-desync above or isaac logs reading a different path than the file holding the errors.

## Fix
Make the viewer entry-aware (read a full EDN form spanning lines) rather than strictly line-at-a-time, and render :throwable. Confirm the log path isaac logs reads matches where errors land.

## REVISED fix (2026-06-29, per Micah): one physical line per entry at the WRITER
Root cause is the WRITER pretty-printing :throwable #error{...} across multiple physical lines. Fix at the source, not the reader:
- WRITER: guarantee exactly ONE physical line per log entry. Serialize the throwable single-line (escape embedded newlines -> \n; stacktrace as \n-escaped text or a vector of frame strings). No pretty-printing in the file.
- VIEWER: keep it line-oriented (correct once the invariant holds) and EXPAND the throwable for display (un-escape \n, indent the stack) — machine-friendly on disk, pretty on screen.
This makes one-line-per-entry an invariant so grep/tail/jq-per-line all work. Couples with isaac-gexx (the throwable it adds MUST be written single-line per this invariant — land together).

## Implementation (work-2, 2026-06-29)
Repo: **isaac-foundation** (on top of gexx `fc84503`)

- **Writer** (`logger.clj`): `normalize-entry-for-disk` converts any `Throwable` in log context to `single-line-throwable` before `pr-str` — covers `:ws/error` and other raw throwable callers
- **Viewer** (`log_viewer.clj`): line-oriented parsing unchanged; `format-entry` shows `:class`/`:message` inline and expands escaped `:stacktrace` on indented continuation lines

Verification: `bb spec spec/isaac/logger_spec.clj spec/isaac/log_viewer_spec.clj` → 67 examples, 0 failures
