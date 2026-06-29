---
# isaac-tqm1
title: 'Server log file lifecycle: durable location, sole ownership, rotation'
status: in-progress
type: feature
created_at: 2026-06-29T17:13:24Z
updated_at: 2026-06-29T17:13:24Z
---

First-principles model (2026-06-29, with Micah). Consolidates the file-LIFECYCLE concerns (was isaac-bwjb rotation + isaac-tykw ownership + log location) into one coherent piece. Content/serialization concerns stay separate (gexx throwable, x2po one-line).

## Principles
1. One writer per file — each process owns its own file (or writes none). The server owns the server log; CLI commands do NOT touch it.
2. Durable, predictable location — under the isaac root (<root>/logs/), never /tmp (wiped on reboot). logs/ auto-created.
3. Bounded — rotation by size/time + retention; self-managing.
4. Attribution is structural — which process = which file; which session/crew/code = fields already in the entry.

## Scope
- DURABLE LOCATION: default the server log to <root>/logs/server.log (was hard-coded /tmp/isaac.log in logger.clj). Auto-create logs/. Configurable; --log-file / ISAAC_LOG_FILE override.
- SOLE OWNERSHIP: short-lived CLI command processes default their file sink OFF (stderr only) so they never write the server's file. Opt-in --log-file/ISAAC_LOG_FILE when debugging a command. (Interactive stderr UX is separate and unaffected.)
- ROTATION: the server's file sink rotates (max-size and/or daily) with retention (keep N). Rotated names server.log.1.. (shift+drop) or date-stamped. Config under a logging schema with sane defaults.

## Acceptance
- Server logs to <root>/logs/server.log (created if missing); not /tmp.
- A CLI command run does not append to the server log file; with --log-file it does.
- The server file rotates at the size/time threshold and retains only N files.
