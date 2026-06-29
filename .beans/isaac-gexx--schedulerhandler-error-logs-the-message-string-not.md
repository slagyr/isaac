---
# isaac-gexx
title: scheduler/handler-error logs the message string, not the throwable (no stacktrace)
status: todo
type: bug
created_at: 2026-06-29T14:50:59Z
updated_at: 2026-06-29T14:50:59Z
---

scheduler/runtime.clj ~207-209 builds the error note with :error-msg (.getMessage ^Exception error) and line 294 logs :error error-msg. Only the exception MESSAGE is kept; the throwable (and its stacktrace) is discarded. So scheduler/handler-error log entries can never carry a stacktrace, unlike :ws/error which logs :throwable #error{...}. Makes scheduler failures hard to diagnose (e.g. the 'Output closed' loop gives no origin).

## Fix
Carry the throwable through the error-note and log it as :throwable (structured #error) like the ws path, in addition to (or instead of) the message string.
