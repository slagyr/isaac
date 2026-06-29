---
# isaac-gexx
title: scheduler/handler-error logs the message string, not the throwable (no stacktrace)
status: todo
type: bug
priority: normal
created_at: 2026-06-29T14:50:59Z
updated_at: 2026-06-29T14:57:05Z
---

scheduler/runtime.clj ~207-209 builds the error note with :error-msg (.getMessage ^Exception error) and line 294 logs :error error-msg. Only the exception MESSAGE is kept; the throwable (and its stacktrace) is discarded. So scheduler/handler-error log entries can never carry a stacktrace, unlike :ws/error which logs :throwable #error{...}. Makes scheduler failures hard to diagnose (e.g. the 'Output closed' loop gives no origin).

## Fix
Carry the throwable through the error-note and log it as :throwable (structured #error) like the ws path, in addition to (or instead of) the message string.

## Couples with isaac-x2po (one-line invariant)
When adding :throwable to scheduler errors, serialize it SINGLE-LINE (escape newlines), per the one-physical-line-per-entry invariant in isaac-x2po. Do NOT pretty-print the #error across lines or it reintroduces the viewer breakage. Land with x2po.
