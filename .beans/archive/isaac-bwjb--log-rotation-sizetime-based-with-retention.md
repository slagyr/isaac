---
# isaac-bwjb
title: 'Log rotation: size/time-based with retention'
status: scrapped
type: feature
priority: normal
created_at: 2026-06-29T16:06:16Z
updated_at: 2026-06-29T17:13:41Z
---

The file logger (isaac-foundation logger.clj save-entry) appends every entry to the log file forever — no rotation. zanebot's /tmp/isaac.log was 13MB+ and growing unbounded.

## Scope
- Rotate the log file by SIZE (e.g. max-size-mb) and/or DAILY, with retention (keep last N files), in the file sink (save-entry / a rotating writer).
- Rotated names: isaac.log -> isaac.log.1 ... (shift + drop oldest) or date-stamped isaac-YYYY-MM-DD.log.
- Config keys under a logging schema: e.g. :logging {:max-size-mb :max-files :rotate :daily?}.
- Defaults sane (e.g. 50MB x 5 files).

## Coupling
Rotation is cleanest when ONE process owns the log file. Today many processes share /tmp/isaac.log (see the origin bean) — concurrent appends + a rotating server race. Pair with the origin-separation bean: the server owns/rotates its file; CLI commands don't fight it.

## Reasons for Scrapping
Consolidated into isaac-tqm1 (Server log file lifecycle) — location + rotation + ownership are one cohesive file-sink concern per the first-principles model.
