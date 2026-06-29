---
# isaac-tqm1
title: 'Server log file lifecycle: durable location, sole ownership, rotation'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-29T17:13:24Z
updated_at: 2026-06-29T18:25:52Z
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

## Scenarios (2026-06-29, reviewed with Micah) — 6, ZERO new gherkin steps
All reuse existing steps: `the clock is fixed at "<ts>"` (foundation), `a file "X" exists with N log entries` (fs-steps), `the Isaac server is started`, `isaac is run with`, `the isaac file "X" exists` / `exists with:` / `does not exist`, `config:`. New config keys (logging.max-bytes, logging.max-days) need a :logging schema. The worker writes the steps' supporting code + speclj specs; this is the planning spec.

### S1 location — the server's active log is <root>/logs/server.log
Root set; server started -> the isaac file "logs/server.log" exists. (Replaces hard-coded /tmp.)

### S2a daily rollover — active server.log -> dated archive on a new day
clock fixed 2026-06-28; file "logs/server.log" with 3 entries; clock fixed 2026-06-29; server started -> "logs/server-20260628.log" exists; "logs/server.log" has a 2026-06-29 entry. (Active is always server.log; rotated-out gets the date.)

### S2b size cap — roll within a day past max-bytes
config logging.max-bytes 2000; clock 2026-06-29; "logs/server.log" with 100 entries (>2000 bytes); server started -> "logs/server-20260629.log" exists; server.log fresh with a 2026-06-29 entry. (Same-day repeats get .1/.2 suffix.)

### S2c retention — drop archives older than max-days
config logging.max-days 30; seed server-20260401.log (>30d) + server-20260601.log; clock 2026-06-29; server started -> 20260601 kept, 20260401 dropped.

### S3a sole ownership — a CLI command creates no server log file by default
isaac is run with "prompt -m 'hi'" -> "logs/server.log" does not exist.

### S3b CLI opt-in — --log-file writes a file
isaac is run with "prompt -m 'hi' --log-file logs/cmd.log" -> "logs/cmd.log" exists with entries.

## Defaults
max-bytes 100MB (runaway guard, not normal path); max-days 30; daily rollover at the configured tz's midnight. Active file always named server.log; archives server-YYYYMMDD.log (+ .N for same-day size rolls).
