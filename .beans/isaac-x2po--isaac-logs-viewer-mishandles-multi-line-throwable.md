---
# isaac-x2po
title: isaac logs viewer mishandles multi-line throwable / error entries
status: todo
type: bug
created_at: 2026-06-29T14:51:00Z
updated_at: 2026-06-29T14:51:00Z
---

isaac logs -> isaac.log-viewer/tail! -> format-line is LINE-ORIENTED: it EDN-parses one line at a time (format-entry dissocs known keys, renders the rest). Multi-line :throwable #error{...} blocks are a partial EDN form per line -> parse fails -> raw fallback, and the throwable's continuation lines render raw too. Result: error entries with throwables don't format (and may desync surrounding lines).

Reported by Micah: isaac logs doesn't format the scheduler 'Output closed' errors. Single-line scheduler entries SHOULD format, so confirm live on zanebot whether it's the throwable-desync above or isaac logs reading a different path than the file holding the errors.

## Fix
Make the viewer entry-aware (read a full EDN form spanning lines) rather than strictly line-at-a-time, and render :throwable. Confirm the log path isaac logs reads matches where errors land.
