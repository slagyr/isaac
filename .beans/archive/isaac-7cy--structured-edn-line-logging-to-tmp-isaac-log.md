---
# isaac-7cy
title: "Structured EDN line logging to /tmp/isaac.log"
status: completed
type: feature
priority: high
created_at: 2026-04-08T01:51:03Z
updated_at: 2026-04-08T15:19:29Z
---

## Description

Chat errors are currently invisible during CLI use. Add a logging foundation that writes one single-line EDN map per entry to /tmp/isaac.log. Logs should be map-based at call sites and serialized with pr-str on one line, never raw strings. Include level-aware logging so different events can emit different data at :error, :warn, :report, :info, and :debug. Design the logger API so a future config option can add JSONL or other sinks without changing instrumentation call sites.

## Scope
- Add logger abstraction for structured event maps
- Write append-only single-line EDN entries to /tmp/isaac.log
- Support log levels and level filtering, including :report
- Include common fields such as timestamp, level, event, and contextual data
- Use macros at call sites so each log entry captures source file and line information automatically
- Instrument chat/provider paths first so invisible errors become visible

## Notes
- EDN lines now; JSONL can be added later as an alternate sink
- Avoid pretty-printing or multiline entries
- This is primarily for local observability/debugging

